package com.codeit.sb13.playlist_for_everyone.global.websocket;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
    private final Map<SocketConnection, Set<SocketConnection>> connectionList = new ConcurrentHashMap<>();

    @Override
    public void register(String sessionId, @Nullable Authentication authentication) {
        register(sessionId, null, authentication);
    }

    @Override
    public void register(String sessionId, @Nullable String subscriptionId, @Nullable Authentication authentication) {
        SocketConnection connection = new SocketConnection(sessionId, subscriptionId, authentication);

        if (connection.subscription() == null) {
            // 구독이 없으면 커넥션 프레임. key 로 새 구독을 추가한다.
            connectionList.computeIfAbsent(connection, k -> new CopyOnWriteArraySet<>());

        } else {

            // 리스트 검색용 키 생성 - SocketConnection(session, null) 가 키.
            SocketConnection key = new SocketConnection(sessionId);

            connectionList.compute(
                    key, (k,v) -> {

                        if(v == null) {
                            log.error("[{}] 구독 등록 에러. {} 세션이 존재하지 않음.",
                                    this.getClass().getSimpleName(),
                                    sessionId);
                            // todo - Internal server error 로 치환.
                            throw new IllegalArgumentException("세션 저장소 등록 에러.");
                        }

                        v.add(connection);
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
        // 저장된 세션에 접근하기 위한 키 객체
        SocketConnection key = new SocketConnection(sessionId, subscriptionId);

        if (key.subscription() == null) {
            // 구독이 없다면 Disconnect 프레임 이라 판단.
            // 세션과 연관된 구독 set 삭제.
            connectionList.remove(key);
        } else {
            SocketConnection sessionKey = new SocketConnection(sessionId);
            connectionList.get(sessionKey).remove(key);
        }
    }

    @Override
    public List<SocketConnection> get(String sessionId) {
        SocketConnection key = new SocketConnection(sessionId);

        Set<SocketConnection> subscribes = connectionList.get(key);
        for (SocketConnection session : connectionList.keySet()){
            if (key.equals(session)) {
                List<SocketConnection> result = new ArrayList<>(subscribes.size() + 1);
                // 원래의 등록된 session 객체.
                result.add(session);
                result.addAll(subscribes);
                return result;
            }
        }
        // 없을 경우 null 반환
        return null;
    }

    @Override
    public SocketConnection get(String sessionId, @Nullable String subscriptionId) {
        SocketConnection key = new SocketConnection(sessionId, subscriptionId);
        if (key.subscription() == null) {
            // 구독ID 가 존재하지 않는다면 session 객체 반환.
            for (SocketConnection session : connectionList.keySet()){
                if (key.equals(session)) {
                    return session;
                }
            }
        } else {
            // 구독 Id 가 존재한다면 Set 내부 구독 연결 반환.
            SocketConnection sessionKey = new SocketConnection(key.session());
            for (SocketConnection subscription : connectionList.get(sessionKey)){
                if (key.equals(subscription)) {
                    return subscription;
                }
            }
        }
        // 대응하는 연결이 없다면 null 반환.
        return null;
    }
}
