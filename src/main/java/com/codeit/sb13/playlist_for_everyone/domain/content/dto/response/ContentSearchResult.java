package com.codeit.sb13.playlist_for_everyone.domain.content.dto.response;

import com.codeit.sb13.playlist_for_everyone.domain.content.entity.Content;

public record ContentSearchResult(
        Content content,
        double averageRating,
        int reviewCount
) {
    public ContentSearchResult(
            Content content,
            Double averageRating,
            Long reviewCount
    ) {
        this(content, averageRating, Math.toIntExact(reviewCount));
    }
}
