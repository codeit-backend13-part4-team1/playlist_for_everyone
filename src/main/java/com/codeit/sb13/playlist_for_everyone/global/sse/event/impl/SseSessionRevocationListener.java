package com.codeit.sb13.playlist_for_everyone.global.sse.event.impl;

import com.codeit.sb13.playlist_for_everyone.domain.auth.event.UserSessionRevokedEvent;
import com.codeit.sb13.playlist_for_everyone.global.sse.event.RevokeEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
public class SseSessionRevocationListener {

    private final RedisRevokeEventPublisher revokeEventPublisher;

    @TransactionalEventListener(fallbackExecution = true)
    public void onUserSessionRevoked(UserSessionRevokedEvent event){
        revokeEventPublisher.publish(new RevokeEvent<>(event.userId(), event.revokedSessionId()));
    }
}
