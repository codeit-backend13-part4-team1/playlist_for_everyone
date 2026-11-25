package com.codeit.sb13.playlist_for_everyone.domain.notification.event;

import com.codeit.sb13.playlist_for_everyone.domain.notification.dto.event.NotificationCreateRequestedEvent;
import com.codeit.sb13.playlist_for_everyone.domain.notification.dto.event.NotificationPublishBatch;
import com.codeit.sb13.playlist_for_everyone.domain.notification.entity.NotificationLevel;
import com.codeit.sb13.playlist_for_everyone.domain.notification.monitoring.NotificationMetrics;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.*;

/** 발행·실패 격리 정책만 직접 검증한다. @Async 프록시와 작업 스레드는 통합 테스트에서 확인한다. */
@ExtendWith(MockitoExtension.class)
@DisplayName("공통 알림 비동기 프로세서의 발행 정책")
class NotificationAsyncProcessorTest {

    @Mock
    private NotificationMetrics metrics;
    @Mock
    private ApplicationEventPublisher eventPublisher;
    @InjectMocks
    private NotificationAsyncProcessor processor;

    @Test
    @DisplayName("전달받은 요청 목록을 내용 변경 없이 순서대로 한 번씩 발행한다.")
    void publishesRequestsInOrderWithoutChangingTheirContents() {
        var first = request();
        var second = request();

        processor.publish(new NotificationPublishBatch("플레이리스트 생성",
                Map.of("playlistId", UUID.randomUUID()), List.of(first, second)));

        var order = inOrder(metrics, eventPublisher);
        order.verify(metrics).recordPublishAttempt(first.source());
        order.verify(eventPublisher).publishEvent(first);
        order.verify(metrics).recordPublishAttempt(second.source());
        order.verify(eventPublisher).publishEvent(second);
        verifyNoMoreInteractions(metrics, eventPublisher);
    }

    @Test
    @ExtendWith(OutputCaptureExtension.class)
    @DisplayName("개별 발행 실패의 식별자만 기록하고 같은 목록의 후속 요청과 다음 업무 이벤트를 계속 처리한다.")
    void isolatesPublishFailureAndLogsOnlyIdentifiers(CapturedOutput output) {
        var failed = request();
        var nextReceiver = request();
        var nextBusinessEvent = request();
        UUID playlistId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        String exceptionMessage = "민감한 예외 내용: " + failed.content();
        doThrow(new IllegalStateException(exceptionMessage)).when(eventPublisher).publishEvent(failed);

        assertThatCode(() -> {
            processor.publish(new NotificationPublishBatch("플레이리스트 생성",
                    Map.of("playlistId", playlistId, "ownerId", ownerId), List.of(failed, nextReceiver)));
            processor.publish(new NotificationPublishBatch("DM",
                    Map.of("directMessageId", UUID.randomUUID()), List.of(nextBusinessEvent)));
        }).doesNotThrowAnyException();

        var order = inOrder(metrics, eventPublisher);
        order.verify(metrics).recordPublishAttempt(failed.source());
        order.verify(eventPublisher).publishEvent(failed);
        order.verify(metrics).recordPublishFailure();
        order.verify(metrics).recordPublishAttempt(nextReceiver.source());
        order.verify(eventPublisher).publishEvent(nextReceiver);
        order.verify(metrics).recordPublishAttempt(nextBusinessEvent.source());
        order.verify(eventPublisher).publishEvent(nextBusinessEvent);
        verifyNoMoreInteractions(metrics, eventPublisher);
        assertThat(output.getAll())
                .contains("플레이리스트 생성 알림 재발행 실패.", "playlistId=" + playlistId,
                        "ownerId=" + ownerId, "receiverId=" + failed.receiverId(), "exceptionType=IllegalStateException")
                .doesNotContain(failed.title(), failed.content(), exceptionMessage);
    }

    private NotificationCreateRequestedEvent request() {
        return new NotificationCreateRequestedEvent(UUID.randomUUID(), "비공개 제목", "비공개 사용자 알림 본문", NotificationLevel.INFO);
    }
}
