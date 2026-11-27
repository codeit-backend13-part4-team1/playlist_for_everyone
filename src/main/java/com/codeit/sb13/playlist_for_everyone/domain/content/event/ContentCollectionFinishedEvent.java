package com.codeit.sb13.playlist_for_everyone.domain.content.event;

import java.util.List;
import java.util.UUID;

/** 배치에서 커밋된 신규 저장 및 갱신 건수와 변경된 콘텐츠 ID 목록 */
public record ContentCollectionFinishedEvent(
        long insertedCount,
        long updatedCount,
        List<UUID> changedContentIds
) {
    public boolean changed() {
        return insertedCount > 0 || updatedCount > 0;
    }
}