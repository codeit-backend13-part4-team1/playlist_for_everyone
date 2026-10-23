package com.codeit.sb13.playlist_for_everyone.domain.notification.dto.event;

import com.codeit.sb13.playlist_for_everyone.domain.notification.entity.NotificationLevel;

import java.util.UUID;

public record NotificationCreateRequestedEvent(UUID receiverId, String title, String content, NotificationLevel level) {
}
