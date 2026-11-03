package com.codeit.sb13.playlist_for_everyone.global.websocket.watching.event;

public record WatchingChatLeaveEvent(
        String sessionId,
        String subscriptionId
) {
}
