package com.codeit.sb13.playlist_for_everyone.global.sse.history;

import java.util.List;

/**
 * 초기 복구에서 확보한 끝 위치와 커서 이후 이벤트 목록.
 * 목록이 비어 있어도 upperBound가 있으면 후속 조회는 그 위치부터 이어 간다.
 * 목록 구조를 복사하며, 필터로 전송할 항목이 줄어도 원본 끝 위치는 유지한다.
 */
public record SseHistorySnapshot(
        SseHistoryPosition upperBound,
        List<SseHistoryEntry> entries
) {
    public SseHistorySnapshot {
        entries = List.copyOf(entries);
    }
}
