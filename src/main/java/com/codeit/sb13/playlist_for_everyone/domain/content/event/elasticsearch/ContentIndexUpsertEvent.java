package com.codeit.sb13.playlist_for_everyone.domain.content.event.elasticsearch;

import java.util.UUID;

/**
 * 콘텐츠 생성·수정 시 Elasticsearch 문서를 생성하거나 갱신하기 위한 이벤트.
 */
public record ContentIndexUpsertEvent (UUID contentId) {
}
