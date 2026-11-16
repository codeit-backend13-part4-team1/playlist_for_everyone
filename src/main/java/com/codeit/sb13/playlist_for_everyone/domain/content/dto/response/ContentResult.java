package com.codeit.sb13.playlist_for_everyone.domain.content.dto.response;

public record ContentResult(
        ContentDto dto,
        String fileKey
) {
}
