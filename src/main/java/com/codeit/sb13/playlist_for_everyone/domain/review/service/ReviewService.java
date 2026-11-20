package com.codeit.sb13.playlist_for_everyone.domain.review.service;

import com.codeit.sb13.playlist_for_everyone.domain.review.dto.condition.ReviewListCondition;
import com.codeit.sb13.playlist_for_everyone.domain.review.dto.request.ReviewCreateRequest;
import com.codeit.sb13.playlist_for_everyone.domain.review.dto.request.ReviewUpdateRequest;
import com.codeit.sb13.playlist_for_everyone.domain.review.dto.response.ReviewDto;
import com.codeit.sb13.playlist_for_everyone.domain.review.dto.response.ReviewResult;
import com.codeit.sb13.playlist_for_everyone.domain.review.mapper.ReviewMapper;
import com.codeit.sb13.playlist_for_everyone.global.dto.response.CursorResponse;
import com.codeit.sb13.playlist_for_everyone.global.file.service.FileService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ReviewService {

    private final ReviewReader reviewReader;
    private final ReviewWriter reviewWriter;
    private final FileService fileService;
    private final ReviewMapper reviewMapper;

    public ReviewDto save(ReviewCreateRequest request, UUID authorId) {
        return reviewWriter.save(request, authorId);
    }

    public CursorResponse<ReviewDto> getReviews(
            UUID contentId,
            ReviewListCondition condition
    ) {
        CursorResponse<ReviewResult> response
                = reviewReader.getReviews(contentId, condition);

        List<ReviewDto> data = response.data().stream()
                .map(reviewResult -> {
                    String key = reviewResult.profileImageKey();

                    String url = key == null
                            ? null
                            : fileService.getFileUrlByKey(key);

                    return reviewMapper.withProfileImageUrl(
                            reviewResult.dto(),
                            url
                    );
                })
                .toList();

        return new CursorResponse<>(
                data,
                response.nextCursor(),
                response.nextIdAfter(),
                response.hasNext(),
                response.totalCount(),
                response.sortBy(),
                response.sortDirection()
        );
    }

    public ReviewDto update(ReviewUpdateRequest request, UUID reviewId, UUID authorId) {
        return reviewWriter.update(request, reviewId, authorId);
    }

    public void delete(UUID reviewId, UUID authorId) {
        reviewWriter.delete(reviewId, authorId);
    }

}
