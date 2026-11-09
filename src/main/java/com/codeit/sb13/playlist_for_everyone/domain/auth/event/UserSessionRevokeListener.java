package com.codeit.sb13.playlist_for_everyone.domain.auth.event;

import com.codeit.sb13.playlist_for_everyone.domain.auth.repository.UserSessionRepository;
import com.codeit.sb13.playlist_for_everyone.domain.user.event.UserLockedEvent;
import com.codeit.sb13.playlist_for_everyone.domain.user.event.UserRoleChangedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;


@Slf4j
@Component
@RequiredArgsConstructor
public class UserSessionRevokeListener {

    private final UserSessionRepository userSessionRepository;
    private final ApplicationEventPublisher eventPublisher;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onRoleChanged(UserRoleChangedEvent event) {
        // GREEN에서 구현
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onLocked(UserLockedEvent event) {
        // GREEN에서 구현
    }
}
