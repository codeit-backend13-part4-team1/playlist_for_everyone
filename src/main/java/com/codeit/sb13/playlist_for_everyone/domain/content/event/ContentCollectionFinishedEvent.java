package com.codeit.sb13.playlist_for_everyone.domain.content.event;

/** 배치에서 커밋된 신규 콘텐츠 건수 */
public record ContentCollectionFinishedEvent(long insertedCount) {
}