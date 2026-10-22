package com.codeit.sb13.playlist_for_everyone.domain.notification.dto.command;

import com.codeit.sb13.playlist_for_everyone.domain.notification.dto.event.NotificationCreateRequestedEvent;
import com.codeit.sb13.playlist_for_everyone.domain.notification.entity.NotificationLevel;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.util.StringUtils;

import java.util.UUID;

public record NotificationCommand(
        @NotNull
        UUID receiverId,
        @NotBlank
        String title,
        @NotBlank
        String content,
        @NotNull
        NotificationLevel level
) {

    public NotificationCommand{
        title = truncateNotificationText(title);
    }

    public static NotificationCommand from(NotificationCreateRequestedEvent event) {
        return new NotificationCommand(event.receiverId(), event.title(), event.content(), event.level());
    }

    private String truncateNotificationText(String value){
        if (!StringUtils.hasText(value)) {
            return value;
        }

        int maxCodePoints = 255;
        String suffix = "...";

        int codePointCount = value.codePointCount(0, value.length());
        if(codePointCount <= maxCodePoints){
            return value;
        }

        int offsetByCodePoints = value.offsetByCodePoints(0, maxCodePoints - suffix.length());

        return value.substring(0,offsetByCodePoints) + suffix;
    }

}
