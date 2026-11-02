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

import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

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