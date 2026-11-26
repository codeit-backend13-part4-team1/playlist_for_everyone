package com.codeit.sb13.playlist_for_everyone.domain.content.event;

/** 배치에서 커밋된 신규 저장 및 갱신 건수 */
public record ContentCollectionFinishedEvent(
        long insertedCount,
        long updatedCount
) {
    public boolean changed() {
        return insertedCount > 0 || updatedCount > 0;
    }
}