package com.codeit.sb13.playlist_for_everyone.domain.content.collector.dto;

import com.codeit.sb13.playlist_for_everyone.domain.content.entity.Content;

public record ContentCollectionResult(
        Content content,
        boolean inserted
) {
}