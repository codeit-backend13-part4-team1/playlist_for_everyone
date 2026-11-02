package com.codeit.sb13.playlist_for_everyone.domain.notification.event;

import com.codeit.sb13.playlist_for_everyone.domain.notification.dto.response.NotificationDto;

import java.util.UUID;

public record NotificationCreatedEvent(
        UUID eventId,
        NotificationDto notificationDto
) {
}
