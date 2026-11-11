package com.codeit.sb13.playlist_for_everyone.global.websocket.watching.event.view;

import com.codeit.sb13.playlist_for_everyone.global.websocket.SessionInfo;

/**
 * 하트비트 프레임에서 일어나는 세션 리프레시를 위한 이벤트.
 * @param info 세션 정보 객체.
 */

public record WatchingSessionRefreshEvent(
        SessionInfo info
) {
}
