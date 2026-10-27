package com.codeit.sb13.playlist_for_everyone.domain.content.dto.response;

import com.codeit.sb13.playlist_for_everyone.domain.content.entity.ContentType;

import java.util.List;
import java.util.UUID;

public record ContentDto(
        UUID id,
        ContentType type,
        String title,
        String description,
        String thumbnailUrl,
        List<String> tags,
        double averageRating,
        int reviewCount,
        int watcherCount
) {
}
