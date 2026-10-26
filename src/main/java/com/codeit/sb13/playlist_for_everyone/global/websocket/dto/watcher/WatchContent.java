package com.codeit.sb13.playlist_for_everyone.global.websocket.dto.watcher;

import java.util.List;
import java.util.UUID;

public record WatchContent(
        UUID id,
        String type,
        String title,
        String description,
        String thumbnailUrl,
        List<String> tags,
        Double averageRating,
        Integer reviewCount
) {
}
