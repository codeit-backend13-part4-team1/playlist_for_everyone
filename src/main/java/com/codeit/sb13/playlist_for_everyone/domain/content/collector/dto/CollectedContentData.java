package com.codeit.sb13.playlist_for_everyone.domain.content.collector.dto;

import com.codeit.sb13.playlist_for_everyone.domain.content.entity.ContentSource;
import com.codeit.sb13.playlist_for_everyone.domain.content.entity.ContentType;
import java.util.List;

public record CollectedContentData(
        ContentType type,
        String title,
        String description,
        String thumbnailUrl,
        ContentSource provider,
        String providerItemId,
        List<String> tags
) {
}