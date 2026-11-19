package com.codeit.sb13.playlist_for_everyone.domain.review.dto.response;

import java.util.UUID;

public record ReviewDto(
        UUID id,
        UUID contentId,
        ReviewAuthorDto authorDto,
        String text,
        double rating
) {
}
