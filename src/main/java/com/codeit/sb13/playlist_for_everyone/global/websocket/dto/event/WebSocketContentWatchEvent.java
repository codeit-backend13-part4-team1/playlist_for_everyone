package com.codeit.sb13.playlist_for_everyone.global.websocket.dto.event;

import com.codeit.sb13.playlist_for_everyone.global.websocket.dto.watcher.WatchingSessionDto;

import java.util.UUID;

public record WebSocketContentWatchEvent(
        UUID contentId,
        WatchingSessionDto content
) {
}
