package com.codeit.sb13.playlist_for_everyone.global.websocket.watching.event;

import java.util.UUID;

/**
 * 컨텐츠가 삭제 되었을 때, 컨텐츠 id 를 발신하는 이벤트.
 * 해당 컨텐츠를 구독한 모든 세션에 대한 연결을 끊는다.
 * contentId : 삭제된 컨텐츠의 id
 */
public record WatchingSessionContentDeleteEvent(
        UUID contentId
){}
