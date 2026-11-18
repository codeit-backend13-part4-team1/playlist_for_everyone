package com.codeit.sb13.playlist_for_everyone.domain.review.dto.request;

import com.codeit.sb13.playlist_for_everyone.domain.review.dto.condition.ReviewListCondition;
import com.codeit.sb13.playlist_for_everyone.global.domain.SortDirection;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import java.util.UUID;

public record ReviewListRequest(
        String cursor,
        UUID idAfter,

        @Min(1)
        int limit,

        @NotNull
        SortDirection sortDirection,

        @NotBlank
        @Pattern(
                regexp = "^(createdAt|rating)$",
                message = "정렬 기준은 createdAt 또는 rating이어야 합니다."
        )
        String sortBy
) {
    /**
     * 요청 DTO를 리뷰 목록 조회 조건으로 변환한다.
     */
    public ReviewListCondition toCondition() {
        return new ReviewListCondition(
                cursor,
                idAfter,
                limit,
                sortDirection,
                sortBy
        );
    }
}
