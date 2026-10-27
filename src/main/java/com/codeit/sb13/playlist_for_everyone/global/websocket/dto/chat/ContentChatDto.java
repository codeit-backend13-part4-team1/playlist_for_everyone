package com.codeit.sb13.playlist_for_everyone.global.websocket.dto.chat;

public record ContentChatDto (
        UserSummary sender,
        String content
) {
}
