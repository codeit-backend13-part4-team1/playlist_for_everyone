package com.codeit.sb13.playlist_for_everyone.domain.content.dto.response;

import com.codeit.sb13.playlist_for_everyone.domain.content.document.ContentDocument;

import java.util.List;
import java.util.UUID;

/**
 * 초기 콘텐츠 색인을 위한 배치 조회 결과.
 *
 * @param documents 색인 대상 문서 목록
 * @param lastId 마지막으로 조회한 콘텐츠 ID
 * @param hasNext 다음 배치 존재 여부
 */
public record ContentIndexBatch(
        List<ContentDocument> documents,
        UUID lastId,
        boolean hasNext

) {
}
