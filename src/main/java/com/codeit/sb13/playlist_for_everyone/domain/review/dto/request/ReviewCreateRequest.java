package com.codeit.sb13.playlist_for_everyone.domain.review.dto.request;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record ReviewCreateRequest (
        @NotNull
        UUID contentId,

        @NotBlank
        String text,

        @DecimalMin("1.0")
        @DecimalMax("5.0")
        double rating
) {
}
