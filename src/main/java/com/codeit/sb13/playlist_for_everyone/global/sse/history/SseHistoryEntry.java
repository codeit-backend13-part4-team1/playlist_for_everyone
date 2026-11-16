package com.codeit.sb13.playlist_for_everyone.global.sse.history;

import com.codeit.sb13.playlist_for_everyone.global.sse.event.SseEvent;
import tools.jackson.databind.JsonNode;

/** 저장 순서의 기준인 위치와 전송할 이벤트를 묶는다. 이벤트 UUID의 문자열 순서로 정렬하지 않는다. */
public record SseHistoryEntry (
        SseHistoryPosition position,
        SseEvent<JsonNode> event
) {
}
