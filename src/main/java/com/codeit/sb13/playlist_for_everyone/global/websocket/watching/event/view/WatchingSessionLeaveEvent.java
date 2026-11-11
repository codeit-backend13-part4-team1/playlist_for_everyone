package com.codeit.sb13.playlist_for_everyone.global.websocket.watching.event.view;

import com.codeit.sb13.playlist_for_everyone.global.websocket.SessionInfo;

public record WatchingSessionLeaveEvent(
        SessionInfo info
) {
}
