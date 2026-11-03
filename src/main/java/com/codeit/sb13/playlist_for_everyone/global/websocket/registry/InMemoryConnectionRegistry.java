package com.codeit.sb13.playlist_for_everyone.global.websocket.registry;

import com.codeit.sb13.playlist_for_everyone.global.websocket.SessionData;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

import java.security.Principal;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;

/**
 * 세션 정보를 저장하는 인메모리 구현체.
 *
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class InMemoryConnectionRegistry implements ConnectionRegistry {

    /*
     * 기본 세션 SocketConnection(sessionId,null) 을 키로 구독을 할 때마다 Set 에 엔티티 객체를 추가한다.
     */
    private final Map<SessionData, Set<SessionData>> connectionList = new ConcurrentHashMap<>();
    private final Map<String, SessionData> keyMap = new ConcurrentHashMap<>();


    @Override
    public void register(String sessionId, @Nullable Principal principal) {
        register(sessionId, null, principal);
    }

    @Override
    public void register(String sessionId, @Nullable String subscriptionId, @Nullable Principal principal) {
        SessionData connection = SessionData.builder()
                .session(sessionId)
                .subscription(subscriptionId)
                .principal(principal)
                .build();

        register(connection);
    }

    @Override
    public void register(SessionData data){
        if (Objects.requireNonNull(data).subscription() == null){
            connectionList.computeIfAbsent(data, k -> {

                // 키맵에 같은 세션 id 가 없어야 세션 키 등록.
                keyMap.compute( k.session(), (mk,mv) ->{
                    if (mv != null) throw new RuntimeException();
                    return k;
                });

                return new CopyOnWriteArraySet<>();
            });
        } else {
            SessionData key = SessionData.builder().session(data.session()).build();
            connectionList.compute(
                    key, (k,v) -> {

                        if(v == null) {
                            log.error("[{}] 구독 등록 에러. {} 세션이 존재하지 않음.",
                                    this.getClass().getSimpleName(),
                                    key.session());
                            // todo - Internal server error 로 치환.
                            throw new IllegalArgumentException("세션 저장소 등록 에러.");
                        }

                        v.add(data);
                        return v;
                    }
            );
        }

    }

    @Override
    public void unregister(String sessionId) {
        unregister(sessionId, null);
    }

    @Override
    public void unregister(String sessionId, @Nullable String subscriptionId) {
        unregister(SessionData.builder().session(sessionId).subscription(subscriptionId).build());
    }

    @Override
    public void unregister(SessionData data) {
        if (Objects.requireNonNull(data).subscription() == null) {
            connectionList.compute(data, (k,v) -> {
                // 세션 id 를 기준으로 키맵에서 제거 후, 세션 등록자 삭제.
                keyMap.remove(k.session());
                return v;
            });
        } else {
            SessionData sessionKey =
                    SessionData
                            .builder()
                            .session(data.session())
                            .build();

            connectionList.computeIfPresent(sessionKey, (k, v) -> {
                v.remove(data);
                return v;
            });
        }
    }

    @Override
    public List<SessionData> get(String sessionId) {
        SessionData key = keyMap.get(sessionId);

        if (key == null) return null;

        Set<SessionData> subscribes = connectionList.get(key);
        List<SessionData> result = new ArrayList<>(subscribes.size() + 1);
        // 원래의 등록된 session 객체.
        result.add(key);
        result.addAll(subscribes);

        return result;
    }

    @Override
    public SessionData get(String sessionId, @Nullable String subscriptionId) {
        SessionData key = SessionData.builder()
                .session(sessionId)
                .subscription(subscriptionId)
                .build();

        if (key.subscription() == null) {
            // 구독ID 가 존재하지 않는다면 session 객체 반환.
            for (SessionData session : connectionList.keySet()){
                if (key.equals(session)) {
                    return session;
                }
            }
        } else {
            // 구독 Id 가 존재한다면 Set 내부 구독 연결 반환.
            SessionData sessionKey = SessionData.builder().session(key.session()).build();
            for (SessionData subscription : connectionList.get(sessionKey)){
                if (key.equals(subscription)) {
                    return subscription;
                }
            }
        }
        // 대응하는 연결이 없다면 null 반환.
        return null;
    }
}
