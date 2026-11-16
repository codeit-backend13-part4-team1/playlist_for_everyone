package com.codeit.sb13.playlist_for_everyone.global.websocket.registry.session;

import com.codeit.sb13.playlist_for_everyone.global.websocket.entity.WSSession;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
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

}
