package com.codeit.sb13.playlist_for_everyone.domain.review.dto.request;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Pattern;

public record ReviewUpdateRequest (
        @Pattern(
                regexp = "(?U)[\\s\\S]*\\S[\\s\\S]*",
                message = "리뷰 내용은 공백일 수 없습니다."
        )
        String text,

        @DecimalMin("1.0")
        @DecimalMax("5.0")
        Double rating
) {
}
