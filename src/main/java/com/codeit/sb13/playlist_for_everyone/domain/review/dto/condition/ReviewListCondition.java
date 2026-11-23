package com.codeit.sb13.playlist_for_everyone.domain.review.dto.condition;

import com.codeit.sb13.playlist_for_everyone.global.domain.SortDirection;

import java.util.UUID;

public record ReviewListCondition(
        String cursor,
        UUID idAfter,
        int limit,
        SortDirection sortDirection,
        String sortBy
) {
}
