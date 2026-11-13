package com.codeit.sb13.playlist_for_everyone.global.websocket.service;

import com.codeit.sb13.playlist_for_everyone.global.websocket.registry.session.WebSocketRegistry;
import com.codeit.sb13.playlist_for_everyone.global.websocket.watching.WatchingSession;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.user.SimpUser;
import org.springframework.messaging.simp.user.SimpUserRegistry;
import org.springframework.stereotype.Component;

import java.util.Objects;

@Slf4j
@Component
@RequiredArgsConstructor
public class WebSocketServiceImpl implements WebSocketService{
    private final SimpUserRegistry userRegistry;
    private final WebSocketRegistry webSocketRegistry;
    private final WatchingSession watchingSession;

    @Override
    public void revokeAuth(String username){
        SimpUser user = userRegistry.getUser(Objects.requireNonNull(username));

        if (user == null) return;

        user.getSessions().forEach(session -> {
            try {
                watchingSession.disconnectWithSessionIds(session.getId());
            } catch (Exception e) {
                log.error("[{}] 세션 등록 해제중 오류가 발생했습니다. id: {}",
                        this.getClass().getSimpleName(),
                        session.getId()
                );
            }
        });
    }
}
