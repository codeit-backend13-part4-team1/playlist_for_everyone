package com.codeit.sb13.playlist_for_everyone.global.websocket.watching.event;

import com.codeit.sb13.playlist_for_everyone.domain.auth.security.SecurityUser;

public record WatchingSessionRefreshEvent(
        String session,
        String subscribe,
        SecurityUser user
) {
}
