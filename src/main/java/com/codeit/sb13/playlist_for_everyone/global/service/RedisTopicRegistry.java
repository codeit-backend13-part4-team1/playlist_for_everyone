package com.codeit.sb13.playlist_for_everyone.global.service;

import com.codeit.sb13.playlist_for_everyone.global.websocket.watching.chat.ChatMessageListener;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.IntStream;

@Slf4j
@Component
@RequiredArgsConstructor
public class RedisTopicRegistry {

    private final RedisBrokerRegister redisBroker;

    private final ChatMessageListener listener;

    // content 를 기반으로 session - subscription 쌍을 관리
    private final ConcurrentHashMap<String, Set<KeyPair>> registerMap = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<KeyPair,String> registerIndex = new ConcurrentHashMap<>();

    // 등록 실패 컨텐츠를 저장하는 셋
    private final Set<String> unregisterFailureSet = new CopyOnWriteArraySet<>();


    static private final int LOCK_SIZE = 1024;
    private final Object[] locks = IntStream.range(0,LOCK_SIZE)
            .mapToObj(i -> new Object()).toArray();


    // KeyPair 는 동시에 같은 값이 2개 이상 존재할 수 없다.
    @RequiredArgsConstructor
    static public class KeyPair{
        private final String session;
        private final String subscription;

        public String toString(){ return session + " : " + subscription; }

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

    public void register(String topic, String sessionId, String subscriptionId){
        KeyPair key = KeyPair.of(sessionId, subscriptionId);
        AtomicBoolean initRegister = new AtomicBoolean(true);
        synchronized (lock(topic)){
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
                registerListener(topic);
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

        synchronized (lock(topic)){
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

    @Scheduled(fixedDelay = 60000) // 60초마다 체크
    public void gc(){
        for(String topic : unregisterFailureSet) {
            synchronized (lock(topic)){
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
    private void registerListener(String topic){
        if (topic == null || topic.isEmpty()) return;
        // 구독 갯수가 1 이상이면 리스너 등록
        Set<KeyPair> keyList = registerMap.get(topic);
        if (!keyList.isEmpty())
            redisBroker.register(listener,topic);
    }

    private void unregisterListener(String topic){
        if (topic == null || topic.isEmpty()) return;
        // 구독 갯수가 0 이라면 리스너 해제
        Set<KeyPair> keyList = registerMap.get(topic);
        if (keyList == null || keyList.isEmpty())
            redisBroker.unregister(listener,topic);
    }

    // 로컬 락 키 반환 매서드
    private Object lock(Object key){
        int hash = key.hashCode();  // 32 비트 해시
        // lock count 가 hash 보다 작을 시, hash 하위 비트를 인덱스에 고르게 분포 시키기 위한 스프레드
        int spread = hash ^ (hash >>> 16);
        return locks[spread & (LOCK_SIZE - 1)];
    }

}
