package com.codeit.sb13.playlist_for_everyone.domain.notification.event.listener;

import com.codeit.sb13.playlist_for_everyone.domain.notification.event.NotificationCreatedEvent;
import com.codeit.sb13.playlist_for_everyone.global.sse.event.Event;
import com.codeit.sb13.playlist_for_everyone.global.sse.event.SseEvent;
import com.codeit.sb13.playlist_for_everyone.global.sse.event.SseEventPublisher;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
public class NotificationSseEventListener {

    private final SseEventPublisher sseEventPublisher;

    @TransactionalEventListener
    public void handleNotificationCreated(NotificationCreatedEvent event){
        sseEventPublisher.publish(new SseEvent<>(
                event.eventId(),
                event.notificationDto().receiverId(),
                Event.NOTIFICATIONS.getName(),
                event.notificationDto()
        ));
    }
}
