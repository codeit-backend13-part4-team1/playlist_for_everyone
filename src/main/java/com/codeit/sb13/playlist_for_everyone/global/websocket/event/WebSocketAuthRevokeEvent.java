package com.codeit.sb13.playlist_for_everyone.global.websocket.event;

import com.codeit.sb13.playlist_for_everyone.domain.auth.security.SecurityUser;

public record WebSocketAuthRevokeEvent(
        SecurityUser user
) {
}
