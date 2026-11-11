package com.codeit.sb13.playlist_for_everyone.global.sse.history;

/** Redis 구현에서는 Stream ID를 담는 내부 처리 위치. 브라우저의 SSE 이벤트 UUID와 다르다. */
public record SseHistoryPosition(String value) {
}
