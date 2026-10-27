package com.codeit.sb13.playlist_for_everyone.global.websocket.payload;

import com.codeit.sb13.playlist_for_everyone.global.websocket.dto.watcher.WatchingSessionDto;

public record WatchingSessionChange(
        ChangeType type,
        WatchingSessionDto watchingSession,
        long watcherCount
) {
}
