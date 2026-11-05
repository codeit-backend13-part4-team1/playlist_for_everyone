package com.codeit.sb13.playlist_for_everyone.global.sse.history;

import com.codeit.sb13.playlist_for_everyone.global.sse.event.SseEvent;
import tools.jackson.databind.JsonNode;

import java.util.UUID;

public interface SseHistoryStore {

    SseHistoryEntry append(SseEvent<JsonNode> event);

    SseHistorySnapshot snapshot(UUID receiverId, UUID lastEventId);

    SseHistoryReadResult readAfter(UUID receiverId, SseHistoryPosition afterPosition);
}
