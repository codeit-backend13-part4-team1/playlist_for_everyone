package com.codeit.sb13.playlist_for_everyone.global.websocket.watching.dto.watcher;

import com.codeit.sb13.playlist_for_everyone.domain.content.entity.ContentType;

import java.util.List;
import java.util.UUID;

public record WatchContent(
        UUID id,
        ContentType type,
        String title,
        String description,
        String thumbnailUrl,
        List<String> tags,
        Double averageRating,
        Integer reviewCount
) {
}
