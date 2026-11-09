package com.codeit.sb13.playlist_for_everyone.global.websocket.registry.session;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketSession;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Component
@RequiredArgsConstructor
public class WebSocketRegistryImpl implements WebSocketRegistry  {

    private final Map<String, WebSocketSession> data = new ConcurrentHashMap<>();


    @Override
    public void register(WebSocketSession session) {
        Objects.requireNonNull(session);
        data.put(session.getId(), session);
    }

    @Override
    public void unregister(WebSocketSession session) {
        if (session != null) {
            data.remove(session.getId(), session);
        }
    }

    @Override
    public Optional<WebSocketSession> query(String sessionId) {
        if (sessionId == null){
            return Optional.empty();
        }
        return Optional.ofNullable(data.get(sessionId));
    }

    @Override
    public void close(String sessionId) throws Exception {
        if(sessionId != null){
            WebSocketSession session = data.get(sessionId);
            if(session != null){
                session.close(CloseStatus.POLICY_VIOLATION);
            }
        }
    }

}
