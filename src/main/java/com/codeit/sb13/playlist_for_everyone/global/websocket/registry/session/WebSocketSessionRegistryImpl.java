package com.codeit.sb13.playlist_for_everyone.global.websocket.registry.session;

import com.codeit.sb13.playlist_for_everyone.global.websocket.entity.WSSession;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Component
@RequiredArgsConstructor
public class WebSocketSessionRegistryImpl implements WebSocketSessionRegistry {

    private final Map<String, WSSession> data = new ConcurrentHashMap<>();


    @Override
    public void register(WSSession session) {
        Objects.requireNonNull(session);
        data.put(session.id(), session);
    }

    @Override
    public void unregister(String sessionId) {
        if (sessionId != null) {
            data.remove(sessionId);
        }
    }

    @Override
    public Optional<WSSession> query(String sessionId) {
        if (sessionId == null){
            return Optional.empty();
        }
        return Optional.ofNullable(data.get(sessionId));
    }

    @Override
    public List<WSSession> queryAll(List<String> sessionIds){
        return sessionIds.stream()
                .map(data::get)
                .filter(Objects::nonNull)
                .toList();
    }

    @Override
    public List<WSSession> findByUserIdAndLoginSessionId(UUID userId, UUID sessionId){
        return data.values().stream()
                .filter(
                        session -> checkSessionUser(session,userId.toString())
                                && checkLoginSessionId(session,sessionId.toString()))
                .toList();
    }

    private boolean checkSessionUser(WSSession session,String targetId){
        if (session == null) return false;
        String userId = session.userId();
        if (userId == null) return false;
        return userId.equals(targetId);
    }

    private boolean checkLoginSessionId(WSSession session, String loginSessionId){
        if (session == null) return false;
        String sessionId = session.loginSessionId();
        if (sessionId == null) return false;
        return sessionId.equals(loginSessionId);
    }

}
