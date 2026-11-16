package com.codeit.sb13.playlist_for_everyone.global.sse.history;

import java.util.List;

/**
 * 후속 조회 결과. gapDetected는 기준 위치를 보관 이력에서 확인할 수 없다는 표시이며,
 * 실제 미수신 이벤트의 유실을 확정하지 않는다. true와 비어 있지 않은 entries가 함께 올 수 있다.
 */
public record SseHistoryReadResult(
        boolean gapDetected,
        List<SseHistoryEntry> entries
) {
    public SseHistoryReadResult {
        entries = List.copyOf(entries);
    }
}
