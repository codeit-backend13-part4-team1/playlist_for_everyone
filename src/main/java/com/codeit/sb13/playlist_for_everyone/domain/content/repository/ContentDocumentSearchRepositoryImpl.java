package com.codeit.sb13.playlist_for_everyone.domain.content.repository;

import com.codeit.sb13.playlist_for_everyone.domain.content.dto.condition.ContentSearchCondition;
import lombok.RequiredArgsConstructor;

import java.util.List;
import java.util.UUID;

@RequiredArgsConstructor
public class ContentDocumentSearchRepositoryImpl implements ContentDocumentSearchRepository {

    /**
     * 검색 조건에 일치하는 콘텐츠 ID를 조회한다.
     * 키워드, 콘텐츠 유형, 태그 조건을 동적으로 적용한다.
     */
    @Override
    public List<UUID> searchContentIds(ContentSearchCondition condition) {
        return List.of();
    }
}
