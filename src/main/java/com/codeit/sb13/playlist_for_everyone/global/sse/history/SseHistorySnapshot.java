package com.codeit.sb13.playlist_for_everyone.global.sse.history;

import java.util.List;

public record SseHistorySnapshot(
        SseHistoryPosition upperBound,
        List<SseHistoryEntry> entries
) {
    public SseHistorySnapshot {
        entries = List.copyOf(entries);
    }
}
