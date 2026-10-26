package com.codeit.sb13.playlist_for_everyone.domain.notification.event;

import com.codeit.sb13.playlist_for_everyone.domain.notification.dto.command.NotificationCommand;
import com.codeit.sb13.playlist_for_everyone.domain.notification.dto.event.NotificationCreateRequestedEvent;
import com.codeit.sb13.playlist_for_everyone.domain.notification.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
@Slf4j
public class NotificationEventListener {

    private final NotificationService service;

    @TransactionalEventListener
    public void handleNotification(NotificationCreateRequestedEvent event){
        NotificationCommand command = NotificationCommand.from(event);
        try {
            service.save(command);
        }catch (Exception e){
            log.warn("알림 저장 실패. receiverId={}", event.receiverId(), e);
        }
    }
}
