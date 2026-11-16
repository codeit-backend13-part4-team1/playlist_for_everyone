package com.codeit.sb13.playlist_for_everyone.global.websocket.service;

import com.codeit.sb13.playlist_for_everyone.global.websocket.registry.session.WebSocketSessionRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Objects;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class WebSocketServiceImpl implements WebSocketService{
    private final WebSocketSessionRegistry webSocketSessionRegistry;


    @Override
    public void disconnectWithAuthRevokeEvent(UUID userId, UUID sessionId){
        Objects.requireNonNull(userId);
        Objects.requireNonNull(sessionId);
        webSocketSessionRegistry.findByUserIdAndSessionId(userId,sessionId)
                .forEach(session -> {
                    try {
                        session.disconnect();
                    } catch (IOException e) {
                        log.error("[{}] 세션 등록 해제중 오류가 발생했습니다. id: {}",
                                this.getClass().getSimpleName(),
                                session.id()
                        );
                        throw new RuntimeException(e);
                    }
                });
    }
}
