package com.codeit.sb13.playlist_for_everyone.domain.notification.event.listener;

import com.codeit.sb13.playlist_for_everyone.domain.notification.dto.response.NotificationDto;
import com.codeit.sb13.playlist_for_everyone.domain.notification.entity.NotificationLevel;
import com.codeit.sb13.playlist_for_everyone.domain.notification.event.NotificationCreatedEvent;
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

        NotificationDto notification = new NotificationDto(
                notificationId,
                LocalDateTime.of(2026, 11, 2, 10, 0),
                receiverId,
                "새 알림",
                "알림 내용",
                NotificationLevel.INFO
        );

        NotificationCreatedEvent event = new NotificationCreatedEvent(eventId, notification);

        listener.handleNotificationCreated(event);

        verify(sseEventPublisher).publish(eventCaptor.capture());
        SseEvent<?> publishedEvent = eventCaptor.getValue();

        assertThat(publishedEvent.eventId()).isEqualTo(eventId);
        assertThat(publishedEvent.receiverId()).isEqualTo(receiverId);
        assertThat(publishedEvent.eventName()).isEqualTo("notifications");
        assertThat(publishedEvent.data()).isEqualTo(notification);
    }

}