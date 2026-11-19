package com.codeit.sb13.playlist_for_everyone.domain.review.service;

import com.codeit.sb13.playlist_for_everyone.domain.review.dto.condition.ReviewListCondition;
import com.codeit.sb13.playlist_for_everyone.domain.review.dto.request.ReviewCreateRequest;
import com.codeit.sb13.playlist_for_everyone.domain.review.dto.request.ReviewUpdateRequest;
import com.codeit.sb13.playlist_for_everyone.domain.review.dto.response.ReviewDto;
import com.codeit.sb13.playlist_for_everyone.domain.review.mapper.ReviewMapper;
import com.codeit.sb13.playlist_for_everyone.global.domain.SortDirection;
import com.codeit.sb13.playlist_for_everyone.global.dto.response.CursorResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.mock;

@ExtendWith(MockitoExtension.class)
@DisplayName("리뷰 서비스 테스트")
public class ReviewServiceTest {

    @Mock
    private ReviewReader reviewReader;

    @Mock
    private ReviewWriter reviewWriter;

    @InjectMocks
    private ReviewService reviewService;

    @Test
    @DisplayName("리뷰 생성 요청을 Writer에 위임하고 결과를 반환한다.")
    void saveReview() {
        // given
        UUID contentId = UUID.randomUUID();
        UUID authorId = UUID.randomUUID();

        ReviewCreateRequest request = new ReviewCreateRequest(
                contentId,
                "재미있는 드라마였어요.",
                4.5
        );

        ReviewDto dto = mock(ReviewDto.class);

        given(reviewWriter.save(request, authorId))
                .willReturn(dto);

        // when
        ReviewDto result = reviewService.save(request, authorId);

        // then
        assertThat(result).isSameAs(dto);
        then(reviewWriter).should().save(request, authorId);
    }

    @Test
    @DisplayName("리뷰 목록 조회를 Reader에 위임하고 페이지네이션 응답을 반환한다.")
    void getReviewList() {
        // given
        UUID contentId = UUID.randomUUID();

        ReviewListCondition condition = new ReviewListCondition(
                null,
                null,
                10,
                SortDirection.DESCENDING,
                "createdAt"
        );

        CursorResponse<ReviewDto> response = new CursorResponse<>(
                List.of(),
                null,
                null,
                false,
                0L,
                "createdAt",
                SortDirection.DESCENDING
        );

        given(reviewReader.getReviews(contentId, condition))
                .willReturn(response);

        // when
        CursorResponse<ReviewDto> result =
                reviewService.getReviews(contentId, condition);

        // then
        assertThat(result).isSameAs(response);
        then(reviewReader).should().getReviews(contentId, condition);
    }

    @Test
    @DisplayName("리뷰 수정 요청을 Writer에 위임하고 결과를 반환한다.")
    void updateReview() {
        // given
        UUID reviewId = UUID.randomUUID();
        UUID authorId = UUID.randomUUID();

        ReviewUpdateRequest request = new ReviewUpdateRequest(
                "수정된 리뷰입니다.",
                4.5
        );

        ReviewDto dto = mock(ReviewDto.class);

        given(reviewWriter.update(request, reviewId, authorId))
                .willReturn(dto);

        // when
        ReviewDto result =
                reviewService.update(request, reviewId, authorId);

        // then
        assertThat(result).isSameAs(dto);
        then(reviewWriter).should().update(request, reviewId, authorId);
    }

    @Test
    @DisplayName("리뷰 삭제 요청을 Writer에 위임한다.")
    void deleteReview() {
        // given
        UUID reviewId = UUID.randomUUID();
        UUID authorId = UUID.randomUUID();

        // when
        reviewService.delete(reviewId, authorId);

        // then
        then(reviewWriter).should().delete(reviewId, authorId);
    }

}
