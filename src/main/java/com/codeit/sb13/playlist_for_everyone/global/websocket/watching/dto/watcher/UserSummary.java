package com.codeit.sb13.playlist_for_everyone.global.websocket.watching.dto.watcher;

import java.util.UUID;

public record UserSummary(
        UUID userId,
        String name,
        String profileImageUrl
) {
}
