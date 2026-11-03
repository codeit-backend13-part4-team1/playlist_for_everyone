package com.codeit.sb13.playlist_for_everyone.domain.notification.event.listener;

import com.codeit.sb13.playlist_for_everyone.domain.notification.dto.response.NotificationDto;
import com.codeit.sb13.playlist_for_everyone.domain.notification.entity.NotificationLevel;
import com.codeit.sb13.playlist_for_everyone.domain.notification.event.NotificationCreatedEvent;
import com.codeit.sb13.playlist_for_everyone.global.exception.sse.SsePublishException;
import com.codeit.sb13.playlist_for_everyone.global.sse.event.SseEvent;
import com.codeit.sb13.playlist_for_everyone.global.sse.event.SseEventPublisher;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * 리스너의 이벤트 변환·발행 요청·실패 처리·로그 내용을 검증하는 단위 테스트다.
 *
 * <p>리스너를 직접 호출하고 SseEventPublisher를 Mock으로 대체한다. 따라서 Spring의
 * AFTER_COMMIT 실행 시점이나 실제 Redis·HTTP 수신을 검증하지 않는다.
 * 트랜잭션 경계는 NotificationIntegrationTest, 실제 수신은 NotificationSseIntegrationTest가 맡는다.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("알림 저장 후 SSE 전송 리스너")
class NotificationSseEventListenerTest {

    @Mock
    private SseEventPublisher sseEventPublisher;

    @InjectMocks
    private NotificationSseEventListener listener;

    @Captor
    private ArgumentCaptor<SseEvent<?>> eventCaptor;

    @Test
    @DisplayName("알림 생성 이벤트를 notifications SSE 이벤트로 한 번 발행한다.")
    void publishesNotificationEventOnceWithOriginalFields(){
        UUID eventId = UUID.randomUUID();
        UUID notificationId = UUID.randomUUID();
        UUID receiverId = UUID.randomUUID();

        NotificationDto notification = createNotificationDto(notificationId, receiverId);

        NotificationCreatedEvent event = new NotificationCreatedEvent(eventId, notification);

        listener.handleNotificationCreated(event);

        verify(sseEventPublisher).publish(eventCaptor.capture());
        SseEvent<?> publishedEvent = eventCaptor.getValue();

        assertThat(publishedEvent.eventId()).isEqualTo(eventId);
        assertThat(publishedEvent.receiverId()).isEqualTo(receiverId);
        assertThat(publishedEvent.eventName()).isEqualTo("notifications");
        assertThat(publishedEvent.data()).isEqualTo(notification);
    }

    @Test
    @DisplayName("SSE 발행에 실패해도 예외를 리스너밖으로 전파하지 않는다.")
    void doesNotPropagatesSsePublishFailure(){
        NotificationDto notification = createNotificationDto();
        NotificationCreatedEvent event = new NotificationCreatedEvent(UUID.randomUUID(), notification);

        SsePublishException failure = new SsePublishException(new IllegalStateException("테스트용 발행 실패"));
        doThrow(failure)
                .when(sseEventPublisher).publish(any());

        assertThatCode(()-> listener.handleNotificationCreated(event))
                .doesNotThrowAnyException();

        verify(sseEventPublisher).publish(any());
    }

    @Test
    @DisplayName("알림 SSE 발행에 실패해도 다음 알림 이벤트를 정상 발행한다.")
    void publishesNextNotificationAfterPublishFailure(){
        NotificationCreatedEvent failedEvent = new NotificationCreatedEvent(UUID.randomUUID(), createNotificationDto());
        NotificationCreatedEvent nextEvent = new NotificationCreatedEvent(UUID.randomUUID(), createNotificationDto());

        SsePublishException failure = new SsePublishException(new IllegalStateException("테스트용 발행 실패"));

        doThrow(failure)
                .doNothing()
                .when(sseEventPublisher).publish(any());

        assertThatCode(()-> {
            listener.handleNotificationCreated(failedEvent);
            listener.handleNotificationCreated(nextEvent);
        }).doesNotThrowAnyException();

        verify(sseEventPublisher, times(2)).publish(eventCaptor.capture());

        assertThat(eventCaptor.getAllValues())
                .extracting(SseEvent::eventId)
                .containsExactly(failedEvent.eventId(), nextEvent.eventId());

        SseEvent<?> nextPublishedEvent = eventCaptor.getAllValues().get(1);
        assertThat(nextPublishedEvent.receiverId())
                .isEqualTo(nextEvent.notificationDto().receiverId());
        assertThat(nextPublishedEvent.data())
                .isEqualTo(nextEvent.notificationDto());
    }

    @Test
    @ExtendWith(OutputCaptureExtension.class)
    @DisplayName("SSE 발행 실패 로그에 식별자와 예외타입을 기록하고 알림 내용은 노출하지 않는다.")
    void logsPublishFailureWithoutExposingNotificationData(CapturedOutput output){
        NotificationDto notification = createNotificationDto();
        NotificationCreatedEvent event = new NotificationCreatedEvent(UUID.randomUUID(), notification);

        // 예외 메시지에도 본문을 넣어, Throwable을 통째로 로그에 넘기는 회귀를 검출한다.
        String sensitiveMessage = "민감한 예외 상세:" + notification.content();

        SsePublishException failure = new SsePublishException(new IllegalStateException(sensitiveMessage));

        doThrow(failure)
                .when(sseEventPublisher).publish(any());

        listener.handleNotificationCreated(event);
        assertThat(output.getAll())
                .contains("notificationId=" + notification.id(),
                        "eventId="+event.eventId(),
                        "receiverId=" + notification.receiverId(),
                        "exceptionType=IllegalStateException")
                .doesNotContain(notification.title(), notification.content(), sensitiveMessage);
    }

    private NotificationDto createNotificationDto(UUID notificationId, UUID receiverId) {
        return new NotificationDto(
                notificationId,
                LocalDateTime.of(2026, 11, 2, 10, 0),
                receiverId,
                "새 알림",
                "알림 내용",
                NotificationLevel.INFO
        );
    }

    private NotificationDto createNotificationDto() {
        return createNotificationDto(UUID.randomUUID(), UUID.randomUUID());
    }

}
