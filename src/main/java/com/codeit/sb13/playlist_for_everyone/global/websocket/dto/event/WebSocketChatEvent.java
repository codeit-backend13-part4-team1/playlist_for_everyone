package com.codeit.sb13.playlist_for_everyone.global.websocket.dto.event;

import com.codeit.sb13.playlist_for_everyone.global.websocket.dto.chat.UserSummary;

import java.util.UUID;

public record WebSocketChatEvent(
        UUID contentId,
        UserSummary user,   // tmp
        String message
) {
}
