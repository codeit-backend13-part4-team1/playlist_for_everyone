package com.codeit.sb13.playlist_for_everyone.global.websocket.watching.event;

import com.codeit.sb13.playlist_for_everyone.global.websocket.watching.dto.watcher.UserSummary;

import java.util.UUID;

public record WatchingChatSendEvent(
        UUID contentId,
        UserSummary user,   // tmp
        String message
) {
}
