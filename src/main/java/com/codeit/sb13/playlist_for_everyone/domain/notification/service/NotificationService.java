package com.codeit.sb13.playlist_for_everyone.domain.notification.service;

import com.codeit.sb13.playlist_for_everyone.domain.notification.dto.repository.NotificationSearchCondition;
import com.codeit.sb13.playlist_for_everyone.domain.notification.dto.response.NotificationDto;
import com.codeit.sb13.playlist_for_everyone.domain.notification.entity.Notification;
import com.codeit.sb13.playlist_for_everyone.domain.notification.repository.NotificationRepository;
import com.codeit.sb13.playlist_for_everyone.global.dto.response.CursorResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional
public class NotificationService {
    private final NotificationRepository notificationRepository;

    @Transactional(readOnly = true)
    public CursorResponse<NotificationDto> getNotifications(NotificationSearchCondition condition) {
        List<Notification> notifications = notificationRepository.search(condition);
        long totalCount = notificationRepository.searchCount(condition);
        boolean hasNext = notifications.size() == condition.limit() + 1;
        List<Notification> page = hasNext
                ? notifications.subList(0, notifications.size() - 1)
                : notifications;
        List<NotificationDto> data = page.stream().map(notification -> new NotificationDto(
                notification.getId(), notification.getCreatedAt(), notification.getReceiver(),
                notification.getTitle(), notification.getContent(), notification.getLevel()
        )).toList();
        NotificationDto last = hasNext ? data.get(data.size() - 1) : null;
        return new CursorResponse<>(data,
                hasNext ? last.createdAt().toString() : null,
                hasNext ? last.id() : null,
                hasNext, totalCount, condition.sortBy().getSortKey(), condition.sortDirection());
    }

    public void confirm(UUID notificationId, UUID receiverId) {
        notificationRepository.deleteByIdAndReceiver(notificationId, receiverId);
    }
}
