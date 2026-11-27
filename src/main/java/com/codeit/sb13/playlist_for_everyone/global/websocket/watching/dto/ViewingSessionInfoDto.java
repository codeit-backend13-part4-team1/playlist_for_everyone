package com.codeit.sb13.playlist_for_everyone.global.websocket.watching.dto;

import java.time.Instant;
import java.util.UUID;

public record ViewingSessionInfoDto(
        UUID subscriptionId,
        Instant createdAt,
        UUID userId,
        UUID contentId
) {
}
