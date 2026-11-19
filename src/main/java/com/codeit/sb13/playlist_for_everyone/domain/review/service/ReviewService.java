package com.codeit.sb13.playlist_for_everyone.domain.review.service;

import com.codeit.sb13.playlist_for_everyone.domain.review.dto.condition.ReviewListCondition;
import com.codeit.sb13.playlist_for_everyone.domain.review.dto.request.ReviewCreateRequest;
import com.codeit.sb13.playlist_for_everyone.domain.review.dto.request.ReviewUpdateRequest;
import com.codeit.sb13.playlist_for_everyone.domain.review.dto.response.ReviewDto;
import com.codeit.sb13.playlist_for_everyone.global.dto.response.CursorResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ReviewService {

    private final ReviewReader reviewReader;
    private final ReviewWriter reviewWriter;

    public ReviewDto save(ReviewCreateRequest request, UUID authorId) {
        return reviewWriter.save(request, authorId);
    }

    public CursorResponse<ReviewDto> getReviews(
            UUID contentId,
            ReviewListCondition condition
    ) {
        CursorResponse<ReviewDto> response = reviewReader.getReviews(contentId, condition);

        return response;
    }

    public ReviewDto update(ReviewUpdateRequest request, UUID reviewId, UUID authorId) {
        return reviewWriter.update(request, reviewId, authorId);
    }

    public void delete(UUID reviewId, UUID authorId) {
        reviewWriter.delete(reviewId, authorId);
    }

}
