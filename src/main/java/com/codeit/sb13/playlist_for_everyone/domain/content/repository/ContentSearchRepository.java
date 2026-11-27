package com.codeit.sb13.playlist_for_everyone.domain.content.repository;

import com.codeit.sb13.playlist_for_everyone.domain.content.dto.condition.ContentSearchCondition;
import com.codeit.sb13.playlist_for_everyone.domain.content.dto.response.ContentSearchResult;

import java.util.List;
import java.util.UUID;

public interface ContentSearchRepository {

    /** 생성일 또는 평점 기준 콘텐츠 목록 조회 */
    List<ContentSearchResult> searchByDatabase(ContentSearchCondition condition, List<UUID> contentIds);

    /** 시청자 수 정렬을 위한 콘텐츠 목록 조회 */
    List<ContentSearchResult> searchForWatcherCount(List<UUID> contentIds);

    /** 검색 조건에 일치하는 콘텐츠 개수 조회 */
    long countBySearchCondition(List<UUID> contentIds);

}
