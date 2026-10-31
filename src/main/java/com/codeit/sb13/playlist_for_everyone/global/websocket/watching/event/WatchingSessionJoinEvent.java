package com.codeit.sb13.playlist_for_everyone.global.websocket.watching.event;

import com.codeit.sb13.playlist_for_everyone.domain.auth.security.SecurityUser;

public record WatchingSessionJoinEvent(
        String contentId,
        String sessionId,
        String subscriptionId,
        SecurityUser user
) {
}
