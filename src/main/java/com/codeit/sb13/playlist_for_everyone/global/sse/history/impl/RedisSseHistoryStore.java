package com.codeit.sb13.playlist_for_everyone.global.sse.history.impl;

import com.codeit.sb13.playlist_for_everyone.global.sse.event.SseEvent;
import com.codeit.sb13.playlist_for_everyone.global.sse.history.*;
import tools.jackson.databind.JsonNode;

import java.util.UUID;

public class RedisSseHistoryStore implements SseHistoryStore {
    @Override
    public SseHistoryEntry append(SseEvent<JsonNode> event) {
        throw new UnsupportedOperationException("SSE 이력 저장 기능이 아직 구현되지 않았습니다.");
    }

    @Override
    public SseHistorySnapshot snapshot(UUID receiverId, UUID lastEventId) {
        throw new UnsupportedOperationException("SSE 복구 스냅샷 조회 기능이 아직 구현되지 않았습니다.");
    }

    @Override
    public SseHistoryReadResult readAfter(UUID receiverId, SseHistoryPosition afterPosition) {
        throw new UnsupportedOperationException("SSE 후속 이력 조회 기능이 아직 구현되지 않았습니다.");
    }
}
