package com.codeit.sb13.playlist_for_everyone.domain.notification.service;

import com.codeit.sb13.playlist_for_everyone.domain.notification.dto.command.NotificationCommand;
import com.codeit.sb13.playlist_for_everyone.domain.notification.dto.repository.NotificationSearchCondition;
import com.codeit.sb13.playlist_for_everyone.domain.notification.dto.response.NotificationDto;
import com.codeit.sb13.playlist_for_everyone.domain.notification.entity.Notification;
import com.codeit.sb13.playlist_for_everyone.domain.notification.repository.NotificationRepository;
import com.codeit.sb13.playlist_for_everyone.global.dto.response.CursorResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional
@Validated
@Slf4j
public class NotificationService {

    private final NotificationRepository notificationRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void save(@Valid @NotNull NotificationCommand command){
        notificationRepository.save(new Notification(command));
    }

    @Transactional(readOnly = true)
    public CursorResponse<NotificationDto> getNotifications(NotificationSearchCondition condition){

        List<Notification> notifications = notificationRepository.search(condition);
        long totalCount = notificationRepository.searchCount(condition);

        boolean hasNext = notifications.size() == condition.limit() + 1;

        List<NotificationDto> data = retrieveNotifications(hasNext, notifications)
                .stream()
                .map(NotificationDto::from)
                .toList();

        return new CursorResponse<>(
                data,
                getNextCursor(hasNext, data),
                getNextIdAfter(hasNext, data),
                hasNext,
                totalCount,
                condition.sortBy().getSortKey(),
                condition.sortDirection()
        ) ;
    }

    public void confirm(UUID notificationId, UUID receiverId){
        notificationRepository.deleteByIdAndReceiver(notificationId, receiverId);
    }

    private UUID getNextIdAfter(boolean hasNext, List<NotificationDto> data) {
        if (hasNext) {
            return data.get(data.size() - 1).id();
        }
        return null;
    }

    private String getNextCursor(boolean hasNext, List<NotificationDto> data) {
        if (hasNext){
            return data.get(data.size() - 1).createdAt().toString();
        }
        return null;
    }

    private List<Notification> retrieveNotifications(boolean hasNext, List<Notification> notifications) {
        if (hasNext) {
            return notifications.subList(0, notifications.size() - 1);
        }
        return notifications;
    }

}
