package com.codeit.sb13.playlist_for_everyone.domain.review.dto.request;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;

public record ReviewUpdateRequest (
        String text,

        @DecimalMin("1.0")
        @DecimalMax("5.0")
        Double rating
) {
}
