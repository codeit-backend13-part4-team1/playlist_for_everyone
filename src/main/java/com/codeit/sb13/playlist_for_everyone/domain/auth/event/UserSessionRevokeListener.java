package com.codeit.sb13.playlist_for_everyone.domain.auth.event;

import com.codeit.sb13.playlist_for_everyone.domain.auth.event.UserSessionRevokedEvent.RevokeReason;
import com.codeit.sb13.playlist_for_everyone.domain.auth.repository.UserSessionRepository;
import com.codeit.sb13.playlist_for_everyone.domain.user.event.UserLockedEvent;
import com.codeit.sb13.playlist_for_everyone.domain.user.event.UserRoleChangedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.Optional;
import java.util.UUID;

/**
 * 권한 변경·계정 잠금이 커밋된 뒤 해당 사용자의 로그인 세션을 종료한다(강제 로그아웃).
 *
 * Redis는 DB 트랜잭션에 포함되지 않으므로, 커밋 이후에만 세션을 종료한다.
 * 변경이 롤백되면 이 리스너는 실행되지 않아 사용자의 로그인이 유지된다.
 *
 * 종료한 세션이 있으면 UserSessionRevokedEvent를 발행한다.
 * SSE·웹소켓은 이 이벤트를 받아 해당 세션의 연결만 종료하도록 확장할 수 있다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class UserSessionRevokeListener {

    private final UserSessionRepository userSessionRepository;
    private final ApplicationEventPublisher eventPublisher;

    /**
     * 요구사항: 권한이 변경되면 해당 사용자는 자동으로 로그아웃된다.
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onRoleChanged(UserRoleChangedEvent event) {
        revoke(event.userId(), RevokeReason.ROLE_CHANGED);
    }

    /**
     * 요구사항: 계정이 잠기면 해당 사용자는 자동으로 로그아웃된다.
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onLocked(UserLockedEvent event) {
        revoke(event.userId(), RevokeReason.LOCKED);
    }

    private void revoke(UUID userId, RevokeReason reason) {
        Optional<UUID> revokedSessionId;

        try {
            revokedSessionId = userSessionRepository.revoke(userId);
        } catch (RuntimeException exception) {
            // Spring은 AFTER_COMMIT 리스너의 예외를 관리자 요청에 전파하지 않고 일반 로그로만 남긴다.
            // 추적할 수 있도록 사용자와 사유를 함께 기록한다.
            // 세션이 남아도 JWT 필터가 요청마다 DB의 권한·잠금 상태를 확인하므로
            // 바뀐 권한으로 판단되고, 잠긴 계정은 인증되지 않는다.
            log.error("로그인 세션 종료에 실패했습니다. userId={}, reason={}", userId, reason, exception);
            return;
        }

        revokedSessionId.ifPresent(sessionId -> publishRevoked(userId, sessionId, reason));
    }

    /**
     * 세션 종료를 알린다. 이 이벤트를 받는 리스너는 발행 스레드에서 실행된다.
     * 리스너가 실패해도 세션은 이미 종료되었으므로, 실패한 처리만 기록한다.
     */
    private void publishRevoked(UUID userId, UUID sessionId, RevokeReason reason) {
        try {
            eventPublisher.publishEvent(new UserSessionRevokedEvent(userId, sessionId, reason));
        } catch (RuntimeException exception) {
            log.error("로그인 세션 종료 후속 처리에 실패했습니다. userId={}, sessionId={}, reason={}",
                    userId, sessionId, reason, exception);
        }
    }
}
