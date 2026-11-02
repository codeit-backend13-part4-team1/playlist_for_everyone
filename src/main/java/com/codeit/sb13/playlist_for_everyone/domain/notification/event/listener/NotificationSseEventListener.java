package com.codeit.sb13.playlist_for_everyone.domain.notification.event.listener;

import com.codeit.sb13.playlist_for_everyone.domain.notification.event.NotificationCreatedEvent;
import com.codeit.sb13.playlist_for_everyone.global.exception.sse.SsePublishException;
import com.codeit.sb13.playlist_for_everyone.global.sse.event.SseEvent;
import com.codeit.sb13.playlist_for_everyone.global.sse.event.SseEventPublisher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
@Slf4j
public class NotificationSseEventListener {

    private final SseEventPublisher sseEventPublisher;

    @TransactionalEventListener
    public void handleNotificationCreated(NotificationCreatedEvent event){
        publishNotification(event);
    }

    private void publishNotification(NotificationCreatedEvent event) {
        try {
            sseEventPublisher.publish(SseEvent.createNotificationEvent(
                    event.eventId(),
                    event.notificationDto().receiverId(),
                    event.notificationDto()
            ));
        } catch (SsePublishException e){
            Throwable cause = e.getCause() != null ? e.getCause() : e;

            log.warn("알림 SSE 발행 실패. notificationId={}, eventId={}, receiverId={}, exceptionType={}",
                    event.notificationDto().id(),
                    event.eventId(),
                    event.notificationDto().receiverId(),
                    cause.getClass().getSimpleName());
        }
    }
}
