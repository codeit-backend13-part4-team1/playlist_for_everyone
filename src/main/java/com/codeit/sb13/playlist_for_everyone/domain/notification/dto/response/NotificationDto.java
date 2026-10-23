package com.codeit.sb13.playlist_for_everyone.domain.notification.dto.response;

import com.codeit.sb13.playlist_for_everyone.domain.notification.entity.Notification;
import com.codeit.sb13.playlist_for_everyone.domain.notification.entity.NotificationLevel;

import java.time.LocalDateTime;
import java.util.UUID;

public record NotificationDto(
        UUID id,
        LocalDateTime createdAt,
        UUID receiverId,
        String title,
        String content,
        NotificationLevel level
) {

}
