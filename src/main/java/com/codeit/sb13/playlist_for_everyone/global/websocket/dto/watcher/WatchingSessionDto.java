package com.codeit.sb13.playlist_for_everyone.global.websocket.dto.watcher;

import java.time.Instant;
import java.util.UUID;

public record WatchingSessionDto(
        UUID id,
        Instant createdAt,
        Watcher watcher,
        WatchContent content
) {
}
