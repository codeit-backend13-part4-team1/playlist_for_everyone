package com.codeit.sb13.playlist_for_everyone.global.websocket;

import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Component
@RequiredArgsConstructor
public class InMemoryConnectionRegistry implements ConnectionRegistry {

    /*
     * 기본 세션 SocketConnection(sessionId,null) 을 키로 구독을 할 때마다 Set 에 엔티티 객체를 추가한다.
     */
    private final Map<SocketConnection, Set<SocketConnection>> connectionList = new ConcurrentHashMap<>();

    @Override
    public void register(String sessionId, @Nullable Authentication authentication) {

    }

    @Override
    public void register(String sessionId, @Nullable String subscriptionId, @Nullable Authentication authentication) {

    }

    @Override
    public void unregister(String sessionId) {

    }

    @Override
    public void unregister(String sessionId, @Nullable String subscriptionId) {

    }

    @Override
    public List<SocketConnection> get(String sessionId) {
        return List.of();
    }

    @Override
    public SocketConnection get(String sessionId, @Nullable String subscriptionId) {
        return null;
    }
}
