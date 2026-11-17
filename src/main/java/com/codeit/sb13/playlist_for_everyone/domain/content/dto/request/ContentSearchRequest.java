package com.codeit.sb13.playlist_for_everyone.domain.content.dto.request;

import com.codeit.sb13.playlist_for_everyone.domain.content.dto.condition.ContentSearchCondition;
import com.codeit.sb13.playlist_for_everyone.domain.content.entity.ContentType;
import com.codeit.sb13.playlist_for_everyone.global.domain.SortDirection;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

import java.util.List;
import java.util.UUID;

public record ContentSearchRequest(
        ContentType typeEqual,
        String keywordLike,
        List<String> tagsIn,
        String cursor,
        UUID idAfter,
        @Min(1) @Max(100) Integer limit,
        SortDirection sortDirection,
        String sortBy
) {
    public ContentSearchRequest {
        if (sortDirection == null) {
            sortDirection = SortDirection.DESCENDING;
        }
        if (sortBy == null) {
            sortBy = "createdAt";
        }
    }

    public ContentSearchCondition toCondition() {
        return new ContentSearchCondition(
                typeEqual,
                keywordLike,
                tagsIn,
                cursor,
                idAfter,
                limit != null ? limit : 20,
                sortDirection != null ? sortDirection : SortDirection.DESCENDING,
                sortBy != null ? sortBy : "createdAt"
        );
    }
}
