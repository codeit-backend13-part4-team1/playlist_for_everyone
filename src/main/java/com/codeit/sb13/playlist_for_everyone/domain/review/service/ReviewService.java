package com.codeit.sb13.playlist_for_everyone.domain.review.service;

import com.codeit.sb13.playlist_for_everyone.domain.content.dto.response.ContentDto;
import com.codeit.sb13.playlist_for_everyone.domain.content.dto.response.ContentResult;
import com.codeit.sb13.playlist_for_everyone.domain.content.dto.response.ContentSearchResult;
import com.codeit.sb13.playlist_for_everyone.domain.content.entity.Content;
import com.codeit.sb13.playlist_for_everyone.domain.content.event.ContentSearchCacheEvictEvent;
import com.codeit.sb13.playlist_for_everyone.domain.content.repository.ContentRepository;
import com.codeit.sb13.playlist_for_everyone.domain.review.dto.condition.ReviewListCondition;
import com.codeit.sb13.playlist_for_everyone.domain.review.dto.request.ReviewCreateRequest;
import com.codeit.sb13.playlist_for_everyone.domain.review.dto.request.ReviewUpdateRequest;
import com.codeit.sb13.playlist_for_everyone.domain.review.dto.response.ReviewDto;
import com.codeit.sb13.playlist_for_everyone.domain.review.entity.Review;
import com.codeit.sb13.playlist_for_everyone.domain.review.mapper.ReviewMapper;
import com.codeit.sb13.playlist_for_everyone.domain.review.repository.ReviewRepository;
import com.codeit.sb13.playlist_for_everyone.domain.user.entity.User;
import com.codeit.sb13.playlist_for_everyone.domain.user.repository.UserRepository;
import com.codeit.sb13.playlist_for_everyone.global.dto.response.CursorResponse;
import com.codeit.sb13.playlist_for_everyone.global.exception.content.ContentNotFoundException;
import com.codeit.sb13.playlist_for_everyone.global.exception.review.ReviewAlreadyExistsException;
import com.codeit.sb13.playlist_for_everyone.global.exception.review.ReviewNotFoundException;
import com.codeit.sb13.playlist_for_everyone.global.exception.user.UserNotFoundException;
import lombok.RequiredArgsConstructor;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import org.springframework.security.access.AccessDeniedException;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ReviewService {

    private final ReviewRepository reviewRepository;
    private final ContentRepository contentRepository;
    private final UserRepository userRepository;
    private final ReviewMapper reviewMapper;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    public ReviewDto save(ReviewCreateRequest request, UUID authorId) {
        Content content = contentRepository.findById(request.contentId())
                .orElseThrow(() -> new ContentNotFoundException(
                        Map.of("contentId", request.contentId())
                ));

        User author = userRepository.findById(authorId)
                .orElseThrow(() -> new UserNotFoundException(
                        Map.of("userId", authorId)
                ));

        Review review = Review.builder()
                .content(content)
                .author(author)
                .text(request.text())
                .rating(request.rating())
                .build();

        Review savedReview;

        try {
            savedReview = reviewRepository.saveAndFlush(review);
        } catch (DataIntegrityViolationException e) {
            if (isReviewUniqueViolation(e)) {
                throw new ReviewAlreadyExistsException(
                        Map.of(
                                "contentId", request.contentId(),
                                "authorId", authorId
                        )
                );
            }
            throw e;
        }

        // 리뷰 생성 시 콘텐츠 검색 캐시 무효화
        eventPublisher.publishEvent(new ContentSearchCacheEvictEvent());

        return reviewMapper.toDto(savedReview);
    }

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

    @Transactional
    public ReviewDto update(ReviewUpdateRequest request, UUID reviewId, UUID authorId) {
        Review review = reviewRepository.findById(reviewId)
                .orElseThrow(() -> new ReviewNotFoundException(
                        Map.of("reviewId", reviewId)
                ));

        if (!review.getAuthor().getId().equals(authorId)) {
            throw new AccessDeniedException("리뷰 작성자만 수정할 수 있습니다.");
        }

        // 기존 평점과 요청 평점 비교
        boolean ratingChanged = request.rating() != null
                && Double.compare(review.getRating(), request.rating()) != 0;

        review.update(request.text(), request.rating());

        // 평점이 변경된 경우에만 콘텐츠 검색 캐시 무효화
        if (ratingChanged) {
            eventPublisher.publishEvent(new ContentSearchCacheEvictEvent());
        }

        return reviewMapper.toDto(review);
    }

    @Transactional
    public void delete(UUID reviewId, UUID authorId) {
        Review review = reviewRepository.findById(reviewId)
                .orElseThrow(() -> new ReviewNotFoundException(
                        Map.of("reviewId", reviewId)
                ));

        // 리뷰 작성자 검증
        if (!review.getAuthor().getId().equals(authorId)) {
            throw new AccessDeniedException("리뷰 작성자만 삭제할 수 있습니다.");
        }

        reviewRepository.delete(review);

        // 리뷰 삭제 시 콘텐츠 검색 캐시 무효화
        eventPublisher.publishEvent(new ContentSearchCacheEvictEvent());
    }

    private boolean isReviewUniqueViolation(DataIntegrityViolationException e) {
        Throwable cause = e;

        while (cause != null) {
            if (cause instanceof ConstraintViolationException cve) {
                return "uk_review_content_author"
                        .equalsIgnoreCase(cve.getConstraintName());
            }
            cause = cause.getCause();
        }

        return false;
    }

}
