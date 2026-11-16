package com.codeit.sb13.playlist_for_everyone.domain.content.service;

import com.codeit.sb13.playlist_for_everyone.domain.content.dto.condition.ContentSearchCondition;
import com.codeit.sb13.playlist_for_everyone.domain.content.dto.response.ContentSummary;
import com.codeit.sb13.playlist_for_everyone.global.config.CacheConfig;
import com.codeit.sb13.playlist_for_everyone.global.dto.response.CursorResponse;
import org.springframework.cache.annotation.Cacheable;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ContentSearchService {

    private final ContentSearchQueryService queryService;

    @Cacheable(
            cacheNames = CacheConfig.CONTENT_SEARCH,
            key = "#condition"
    )
    public CursorResponse<ContentSummary> search(
            ContentSearchCondition condition
    ) {
        return null;
    }

}
