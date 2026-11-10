package com.codeit.sb13.playlist_for_everyone.domain.content.repository;

import com.codeit.sb13.playlist_for_everyone.domain.content.dto.condition.ContentSearchCondition;
import com.codeit.sb13.playlist_for_everyone.domain.content.dto.response.ContentSummary;

import java.util.List;

public interface ContentSearchRepository {

    List<ContentSummary> search(ContentSearchCondition condition);

    long countBySearchCondition(ContentSearchCondition condition);

}
