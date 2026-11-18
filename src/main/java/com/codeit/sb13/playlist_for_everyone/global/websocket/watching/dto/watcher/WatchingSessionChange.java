package com.codeit.sb13.playlist_for_everyone.global.websocket.watching.dto.watcher;

public record WatchingSessionChange(
        ChangeType type,
        WatchingSessionDto watchingSession,
        long watcherCount
) {
}
