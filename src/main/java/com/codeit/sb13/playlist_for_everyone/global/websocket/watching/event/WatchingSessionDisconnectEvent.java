package com.codeit.sb13.playlist_for_everyone.global.websocket.watching.event;

import java.util.UUID;

/**
 * 세션 정보가 삭제되어야 할 때, 발행될 이벤트.
 * 컨텐츠 삭제 시 활성세션을 막기 위해 사용할 이벤트 이다.
 * todo - 활성 세션이 남아있어도 등록 세션과 브로커 구독을 꺼야하는지 추후 고려.
 * @param contentId
 */
public record WatchingSessionDisconnectEvent(
        UUID contentId
) {
}
