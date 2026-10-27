package com.codeit.sb13.playlist_for_everyone.global.websocket.dto.chat;

import java.util.UUID;

public record UserSummary(
        UUID userId,
        String name,
        String profileImageUrl
) {
}
