package com.codeit.sb13.playlist_for_everyone.domain.review.dto.response;

import com.codeit.sb13.playlist_for_everyone.domain.user.dto.response.UserDto;

import java.util.UUID;

public record ReviewDto(
        UUID id,
        UUID contentId,
        UserDto author,
        String text,
        double rating
) {
}
