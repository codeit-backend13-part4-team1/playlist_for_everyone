package com.codeit.sb13.playlist_for_everyone.domain.review.dto.request;

import com.codeit.sb13.playlist_for_everyone.domain.review.dto.condition.ReviewListCondition;
import com.codeit.sb13.playlist_for_everyone.global.domain.SortDirection;
import jakarta.validation.constraints.*;

import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.UUID;

public record ReviewListRequest(
        String cursor,
        UUID idAfter,

        @Min(1)
        @Max(20)
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
     * 커서와 보조 커서는 함께 전달하거나 모두 생략해야 한다.
     */
    @AssertTrue(message = "cursor와 idAfter는 함께 전달해야 합니다.")
    public boolean isCursorPairValid() {
        return (cursor == null) == (idAfter == null);
    }

    /**
     * 커서가 정렬 기준에 맞는 형식인지 검증한다.
     */
    @AssertTrue(message = "정렬 기준에 맞지 않는 커서 형식입니다.")
    public boolean isCursorFormatValid() {
        if (cursor == null || sortBy == null) {
            return true;
        }

        try {
            return switch (sortBy) {
                case "createdAt" -> {
                    LocalDateTime.parse(cursor);
                    yield true;
                }
                case "rating" -> {
                    double rating = Double.parseDouble(cursor);
                    yield Double.isFinite(rating);
                }
                default -> true; // sortBy는 @Pattern에서 검증
            };
        } catch (DateTimeParseException | NumberFormatException e) {
            return false;
        }
    }

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
