package com.codeit.sb13.playlist_for_everyone.global.websocket.watching.dto.chat;

import com.codeit.sb13.playlist_for_everyone.domain.user.dto.response.UserSummary;

public record ContentChatDto (
        UserSummary sender,
        String content
) {
}
