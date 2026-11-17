package com.codeit.sb13.playlist_for_everyone.domain.content.dto.condition;

import com.codeit.sb13.playlist_for_everyone.domain.content.entity.ContentType;
import com.codeit.sb13.playlist_for_everyone.global.domain.SortDirection;

import java.util.List;
import java.util.UUID;

public record ContentSearchCondition(
        ContentType typeEqual,
        String keywordLike,
        List<String> tagsIn,
        String cursor,
        UUID idAfter,
        int limit,
        SortDirection sortDirection,
        String sortBy
) {
}
