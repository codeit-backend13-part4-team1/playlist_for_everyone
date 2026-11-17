package com.codeit.sb13.playlist_for_everyone.domain.content.repository;

import com.codeit.sb13.playlist_for_everyone.domain.content.dto.condition.ContentSearchCondition;
import com.codeit.sb13.playlist_for_everyone.domain.content.dto.response.ContentSearchResult;

import java.util.List;

public interface ContentSearchRepository {

    List<ContentSearchResult> searchByDatabase(ContentSearchCondition condition);

    List<ContentSearchResult> searchForWatcherCount(ContentSearchCondition condition);

    long countBySearchCondition(ContentSearchCondition condition);

}
