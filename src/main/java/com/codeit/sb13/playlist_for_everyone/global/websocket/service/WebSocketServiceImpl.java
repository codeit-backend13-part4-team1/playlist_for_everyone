package com.codeit.sb13.playlist_for_everyone.global.websocket.service;

import com.codeit.sb13.playlist_for_everyone.global.websocket.registry.session.WebSocketSessionRegistry;
import com.codeit.sb13.playlist_for_everyone.global.websocket.watching.service.WatchingSessionService;
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
    private final WatchingSessionService watchingSessionService;


    @Override
    public void disconnectWithAuthRevokeEvent(UUID userId, UUID sessionId){
        Objects.requireNonNull(userId);
        Objects.requireNonNull(sessionId);
        webSocketSessionRegistry.findByUserIdAndLoginSessionId(userId,sessionId)
                .forEach(session -> {
                    try {
                        try {
                            watchingSessionService.cleanUpWithSessionId(session.id());
                        } catch (RuntimeException e) {
                            // todo - 추후 재처리 기능 추가.
                            log.error("[{}] 비즈니스 구독 리소스 등록 해제중 오류가 발생했습니다. id: {}",
                                    this.getClass().getSimpleName(),
                                    session.id()
                            );
                        }
                        session.disconnect();
                    } catch (IOException e) {
                        log.error("[{}] 세션 등록 해제중 오류가 발생했습니다. id: {}",
                                this.getClass().getSimpleName(),
                                session.id()
                        );
                    }
                });
    }
}
