package com.codeit.sb13.playlist_for_everyone.global.websocket.watching.event;

import com.codeit.sb13.playlist_for_everyone.domain.auth.security.SecurityUser;

/**
 * 하트비트 프레임에서 일어나는 세션 리프레시를 위한 이벤트.
 * @param session 세션 Id.
 * @param subscribe 구독 id.
 * @param user 사용자 인증 정보.
 */

public record WatchingSessionRefreshEvent(
        String session,
        String subscribe,
        SecurityUser user
) {
}
