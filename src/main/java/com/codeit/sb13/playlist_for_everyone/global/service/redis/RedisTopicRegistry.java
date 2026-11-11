package com.codeit.sb13.playlist_for_everyone.global.service.redis;

import com.codeit.sb13.playlist_for_everyone.global.service.lock.Locks;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 외부에서 메세지 리스너를 받아와서 토픽에 대해 등록/해제를 관리하는 클래스
 * 외부 리스너 - 토픽 단위에, 토픽 - 구독 수 단위로
 * 두 가지 리스트를 통해 리스너 별 구독 을 관리한다.
 * 동시성은 로컬 로직에 의한 동시성만 고려하면 된다.
 */

@Slf4j
@Component
@RequiredArgsConstructor
public class RedisTopicRegistry {

    private final Locks locks;
    private final RedisBrokerRegister redisBroker;

    private final Map<String, RedisMessageListener> listenerMap = new ConcurrentHashMap<>();
    // content(topic) 를 기반으로 session - subscription 쌍을 관리
    private final Map<String, Set<KeyPair>> registerMap = new ConcurrentHashMap<>();
    private final Map<KeyPair,String> registerIndex = new ConcurrentHashMap<>();

    // 등록 실패 컨텐츠를 저장하는 셋
    private final Set<String> unregisterFailureSet = new CopyOnWriteArraySet<>();

    // KeyPair 는 동시에 같은 값이 2개 이상 존재할 수 없다.
    // 동기 작업을 위해 세션 저장소도 해당 클래스만 접근하도록 구성.
    @RequiredArgsConstructor
    static public class KeyPair{
        private final String session;
        private final String subscription;

        public String toString(){ return session + " : " + subscription; }

        public String session(){ return session; }

        @Override
        public boolean equals(Object other){
            if(!(other instanceof KeyPair otherKeyPair)) return false;
            return session.equals(otherKeyPair.session) && subscription.equals(otherKeyPair.subscription);
        }

        @Override
        public int hashCode(){
            return Objects.hash(session, subscription);
        }

        public boolean inSession(String session){
            return session.equals(this.session);
        }

        static public KeyPair of(String session, String subscription){
            return new KeyPair(session, subscription);
        }
    }

    public void register(
            String topic,
            String sessionId,
            String subscriptionId,
            RedisMessageListener listener
    ){
        KeyPair key = KeyPair.of(sessionId, subscriptionId);
        AtomicBoolean initRegister = new AtomicBoolean(true);
        synchronized (locks.lock(topic)){
            registerMap.computeIfAbsent(
                    topic,
                    k -> new CopyOnWriteArraySet<>());

            // 이전에 메세지 리스너가 구독되었다면 구독 안 함.
            if (!registerMap.get(topic).isEmpty())
                initRegister.set(false);

            registerMap.computeIfPresent(topic, (k,v) -> {
                // 기존 등록된 id 변경 하지 않음.
                if (v.contains(key)){
                    return v;
                }

                v.add(KeyPair.of(sessionId, subscriptionId));
                registerIndex.put(key, topic);
                return v;
            });

            // 이미 등록된 경우 재등록 안 함.
            if (!initRegister.get()) return;
            // 미 해제 컨텐츠 채널에 등록 된 경우 재등록 안 함.
            if (unregisterFailureSet.contains(topic)) {
                unregisterFailureSet.remove(topic);
                return;
            }

            try {
                registerListener(topic, listener);
            } catch (RuntimeException e) {
                log.error("[{}] 레디스 리스너 등록 실패 - topic: {}",
                        this.getClass().getSimpleName(),
                        topic,
                        e);
                // 등록 실패시 키 삭제.
                registerMap.get(topic).remove(key);
                registerIndex.remove(key);
            }
        }
    }

    public void unregister(String sessionId, String subscriptionId){
        KeyPair key = KeyPair.of(sessionId, subscriptionId);
        unregister(key);
    }

    private void unregister(KeyPair key){
        String topic = registerIndex.get(key);

        if (topic == null) return;

        synchronized (locks.lock(topic)){
            // 등록이 없다면 반환(동시 접속시 선두 매서드만 지움이 가능하도록)
            if(registerMap.get(topic) == null) return;

            registerMap.computeIfPresent(topic, (k,v) -> {
                v.remove(key);
                registerIndex.remove(key);
                return v;
            });
            try {
                unregisterListener(topic);
            } catch (RuntimeException e){
                log.error("[{}] 레디스 리스너 해제 실패 - topic: {}",
                        this.getClass().getSimpleName(),
                        topic,
                        e);

                // 삭제 실패시 삭제 대기열 등록
                unregisterFailureSet.add(topic);
            }
            // 등록값이 비어있으면 삭제.
            if (registerMap.get(topic).isEmpty())
                registerMap.remove(topic);
        }
    }

    // 세션이 삭제되는 도중 같은 세션이 다시 등록되면
    public void disconnect(String sessionId){
        List<KeyPair> registerIndexKeys = registerIndex.keySet().stream()
                .filter(key -> key.inSession(sessionId))
                .toList();

        for (KeyPair key : registerIndexKeys){
            unregister(key);
        }

    }

    public List<String> disconnectWithContentId(String contentId){
        List<String> topics = new ArrayList<>();
        if (registerMap.get(contentId) == null)
            return topics;
        for (KeyPair topic : registerMap.get(contentId)){
            unregister(topic);
            topics.add(topic.session());
        }
        return topics;
    }

    @Scheduled(fixedDelay = 60000) // 60초마다 체크
    public void gc(){
        for(String topic : unregisterFailureSet) {
            synchronized (locks.lock(topic)){
                if (registerMap.get(topic) != null){
                    if (registerMap.get(topic).isEmpty()){
                        unregisterFailureSet.remove(topic);
                        continue;
                    }
                }

                try {
                    unregisterListener(topic);
                    unregisterFailureSet.remove(topic);
                } catch (RuntimeException e) {
                    log.error("[{}] .gc() - 레디스 리스너 해제 실패 - topic : {}",
                            this.getClass().getSimpleName(),
                            topic,
                            e);
                }
            }
        }
    }


    /*
    편의 매서드
     */
    private void registerListener(String topic,RedisMessageListener listener){
        if (topic == null || topic.isEmpty()) return;
        // 구독 갯수가 1 이상이면 리스너 등록
        Set<KeyPair> keyList = registerMap.get(topic);
        if (!keyList.isEmpty()) {
            listenerMap.compute(topic, (k, v) -> {
                if (listener.equals(listenerMap.get(k)))
                    // 이미 존재하는 리스너가 있고, 어떤 게 연결이 끊긴건지 분간할 수 없음.
                    throw new RuntimeException();
                redisBroker.register(listener, topic);
                return listener;
            });
        }
    }

    private void unregisterListener(String topic){
        if (topic == null || topic.isEmpty()) return;
        // 구독 갯수가 0 이라면 리스너 해제
        Set<KeyPair> keyList = registerMap.get(topic);
        if (keyList == null || keyList.isEmpty())
            listenerMap.compute(topic, (k,v) -> {
                if (v != null){
                    redisBroker.unregister(v,k);
                }
                return null;    // 키쌍 삭제.
            });

    }

}
