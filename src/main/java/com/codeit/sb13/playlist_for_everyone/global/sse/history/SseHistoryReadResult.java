package com.codeit.sb13.playlist_for_everyone.global.sse.history;

import java.util.List;

public record SseHistoryReadResult(
        boolean gapDetected,
        List<SseHistoryEntry> entries
) {
    public SseHistoryReadResult {
        entries = List.copyOf(entries);
    }
}
