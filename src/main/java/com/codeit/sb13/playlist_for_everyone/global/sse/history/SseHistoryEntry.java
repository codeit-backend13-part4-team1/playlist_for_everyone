package com.codeit.sb13.playlist_for_everyone.global.sse.history;

import com.codeit.sb13.playlist_for_everyone.global.sse.event.SseEvent;
import tools.jackson.databind.JsonNode;

public record SseHistoryEntry (
        SseHistoryPosition position,
        SseEvent<JsonNode> event
) {
}
