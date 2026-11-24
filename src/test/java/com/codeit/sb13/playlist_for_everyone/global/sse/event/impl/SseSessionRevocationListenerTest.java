package com.codeit.sb13.playlist_for_everyone.global.sse.event.impl;

import com.codeit.sb13.playlist_for_everyone.domain.auth.event.UserSessionRevokedEvent;
import com.codeit.sb13.playlist_for_everyone.domain.auth.event.UserSessionRevokedEvent.RevokeReason;
import com.codeit.sb13.playlist_for_everyone.global.sse.event.RevokeEvent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.transaction.event.TransactionalEventListenerFactory;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

@DisplayName("인증 세션 폐기 이벤트의 SSE 발행 연동")
class SseSessionRevocationListenerTest {

    @Test
    @DisplayName("트랜잭션 밖에서 세션을 폐기하면 사용자 ID와 폐기된 세션 ID를 한 번 발행한다.")
    void publishesRevokedSessionOutsideTransaction() {
        UUID userId = UUID.randomUUID();
        UUID revokedSessionId = UUID.randomUUID();
        RedisRevokeEventPublisher publisher = mock(RedisRevokeEventPublisher.class);

        new ApplicationContextRunner()
                .withUserConfiguration(SseSessionRevocationListener.class, TransactionalEventListenerFactory.class)
                .withBean(RedisRevokeEventPublisher.class, () -> publisher)
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();

                    context.publishEvent(new UserSessionRevokedEvent(userId, revokedSessionId, RevokeReason.DUPLICATE_LOGIN));

                    verify(publisher).publish(new RevokeEvent<>(userId, revokedSessionId));
                    verifyNoMoreInteractions(publisher);
                });
    }
}
