package com.codeit.sb13.playlist_for_everyone.global.websocket.event;

import com.codeit.sb13.playlist_for_everyone.global.websocket.watching.event.WatchingSessionContentDeleteEvent;
import com.codeit.sb13.playlist_for_everyone.global.websocket.watching.service.WatchingSessionService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.UUID;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@SpringBootTest(properties = "websocket.multiple=false")
@DisplayName("로컬 콘텐츠 삭제 이벤트 리스너 테스트")
class WatchingSessionLocalEventListenerTest {

    @Autowired
    private ApplicationEventPublisher eventPublisher;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @MockitoBean
    private WatchingSessionService watchingSessionService;

    @Test
    @DisplayName("콘텐츠 삭제 이벤트는 커밋 이후 시청 세션을 정리한다")
    void cleanupAfterCommit() {
        UUID contentId = UUID.randomUUID();

        transactionTemplate.executeWithoutResult(status -> {
            eventPublisher.publishEvent(
                    new WatchingSessionContentDeleteEvent(contentId)
            );

            // 커밋 전에는 실행되지 않아야 한다.
            verify(watchingSessionService, never())
                    .cleanUpWithContentId(contentId);
        });

        // 커밋 이후 실행
        verify(watchingSessionService)
                .cleanUpWithContentId(contentId);
    }

    @Test
    @DisplayName("트랜잭션 롤백 시 시청 세션을 정리하지 않는다")
    void cleanupNotExecutedAfterRollback() {
        UUID contentId = UUID.randomUUID();

        transactionTemplate.executeWithoutResult(status -> {
            eventPublisher.publishEvent(
                    new WatchingSessionContentDeleteEvent(contentId)
            );

            status.setRollbackOnly();
        });

        verify(watchingSessionService, never())
                .cleanUpWithContentId(contentId);
    }

    @Test
    @DisplayName("트랜잭션 없이 이벤트가 발행되면 시청 세션을 정리하지 않는다")
    void cleanupWithoutTransaction() {
        // given
        UUID contentId = UUID.randomUUID();

        // when
        eventPublisher.publishEvent(
                new WatchingSessionContentDeleteEvent(contentId)
        );

        // then
        verify(watchingSessionService, never()).cleanUpWithContentId(contentId);
    }

}
