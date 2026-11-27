package com.codeit.sb13.playlist_for_everyone.domain.content.event.elasticsearch;

import java.util.UUID;

/**
 * 콘텐츠 삭제 시 Elasticsearch 문서를 삭제하기 위한 이벤트.
 */
public record ContentIndexDeleteEvent (UUID contentId) {
}
