package com.codeit.sb13.playlist_for_everyone.domain.review.service;

import com.codeit.sb13.playlist_for_everyone.domain.review.dto.condition.ReviewListCondition;
import com.codeit.sb13.playlist_for_everyone.domain.review.dto.response.ReviewDto;
import com.codeit.sb13.playlist_for_everyone.domain.review.entity.Review;
import com.codeit.sb13.playlist_for_everyone.domain.review.mapper.ReviewMapper;
import com.codeit.sb13.playlist_for_everyone.domain.review.repository.ReviewRepository;
import com.codeit.sb13.playlist_for_everyone.global.dto.response.CursorResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ReviewReader {

    private final ReviewRepository reviewRepository;
    private final ReviewMapper reviewMapper;

    @Transactional(readOnly = true)
    public CursorResponse<ReviewDto> getReviews(
            UUID contentId,
            ReviewListCondition condition
    ) {
        List<Review> reviews =
                reviewRepository.getReviewsByContentId(contentId, condition);

        // 다음 페이지 존재 여부 확인
        boolean hasNext = reviews.size() > condition.limit();

        // 요청한 개수만 반환
        List<Review> page = reviews.stream()
                .limit(condition.limit())
                .toList();

        // 다음 페이지 커서 생성
        String nextCursor = null;
        UUID nextIdAfter = null;

        if (hasNext && !page.isEmpty()) {
            Review lastReview = page.get(page.size() - 1);

            nextCursor = switch (condition.sortBy()) {
                case "createdAt" -> lastReview.getCreatedAt().toString();
                case "rating" -> String.valueOf(lastReview.getRating());
                default -> throw new IllegalArgumentException(
                        "지원하지 않는 정렬 기준: " + condition.sortBy()
                );
            };

            nextIdAfter = lastReview.getId();
        }

        long totalCount = reviewRepository.countByContentId(contentId);

        // Review -> ReviewDto 변환
        List<ReviewDto> data = page.stream()
                .map(reviewMapper::toDto)
                .toList();

        return new CursorResponse<>(
                data,
                nextCursor,
                nextIdAfter,
                hasNext,
                totalCount,
                condition.sortBy(),
                condition.sortDirection()
        );
    }

}
