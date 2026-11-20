package com.codeit.sb13.playlist_for_everyone.domain.review.service;

import com.codeit.sb13.playlist_for_everyone.domain.content.entity.Content;
import com.codeit.sb13.playlist_for_everyone.domain.content.entity.ContentType;
import com.codeit.sb13.playlist_for_everyone.domain.review.dto.condition.ReviewListCondition;
import com.codeit.sb13.playlist_for_everyone.domain.review.dto.response.ReviewAuthorDto;
import com.codeit.sb13.playlist_for_everyone.domain.review.dto.response.ReviewDto;
import com.codeit.sb13.playlist_for_everyone.domain.review.dto.response.ReviewResult;
import com.codeit.sb13.playlist_for_everyone.domain.review.entity.Review;
import com.codeit.sb13.playlist_for_everyone.domain.review.mapper.ReviewMapper;
import com.codeit.sb13.playlist_for_everyone.domain.review.repository.ReviewRepository;
import com.codeit.sb13.playlist_for_everyone.global.domain.SortDirection;
import com.codeit.sb13.playlist_for_everyone.global.dto.response.CursorResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.*;
import static org.mockito.Mockito.mock;

@ExtendWith(MockitoExtension.class)
@DisplayName("리뷰 목록 조회 트랜잭션 테스트")
public class ReviewReaderTest {

    @Mock
    private ReviewRepository reviewRepository;

    @Mock
    private ReviewMapper reviewMapper;

    @InjectMocks
    private ReviewReader reviewReader;

    private Content content;
    private List<Review> reviews;
    private List<ReviewResult> reviewResults;

    @BeforeEach
    void setUp() {
        content = Content.createAdminContent(
                ContentType.tvSeries,
                "드라마1",
                "드라마설명1",
                null
        );

        ReflectionTestUtils.setField(content, "id", UUID.randomUUID());

        List<Review> reviewList = new ArrayList<>();
        List<ReviewResult> resultList = new ArrayList<>();

        for (int i = 1; i <= 4; i++) {
            UUID reviewId = UUID.randomUUID();
            UUID authorId = UUID.randomUUID();

            Review review = mock(Review.class);

            ReviewDto dto = new ReviewDto(
                    reviewId,
                    content.getId(),
                    new ReviewAuthorDto(
                            authorId,
                            "작성자" + i,
                            null
                    ),
                    "리뷰" + i,
                    5.0 - i
            );

            ReviewResult result = new ReviewResult(
                    dto,
                    "profiles/author" + i + ".png"
            );

            reviewList.add(review);
            resultList.add(result);
        }

        reviews = List.copyOf(reviewList);
        reviewResults = List.copyOf(resultList);
    }

    @Test
    @DisplayName("리뷰 목록을 조회할 수 있다.")
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

        given(reviewRepository.getReviewsByContentId(contentId, condition)).willReturn(reviews);

        given(reviewRepository.countByContentId(contentId)).willReturn(4L);

        for (int i = 0; i < 4; i++) {
            given(reviewMapper.toResult(reviews.get(i)))
                    .willReturn(reviewResults.get(i));
        }

        // when
        CursorResponse<ReviewResult> result = reviewReader.getReviews(contentId, condition);

        // then
        assertThat(result.data()).containsExactlyElementsOf(reviewResults);
        assertThat(result.data())
                .extracting(ReviewResult::profileImageKey)
                .containsExactly(
                        "profiles/author1.png",
                        "profiles/author2.png",
                        "profiles/author3.png",
                        "profiles/author4.png"
                );

        assertThat(result.hasNext()).isFalse();
        assertThat(result.nextCursor()).isNull();
        assertThat(result.nextIdAfter()).isNull();
        assertThat(result.totalCount()).isEqualTo(4L);

        then(reviewRepository).should().getReviewsByContentId(contentId, condition);
    }

    @Test
    @DisplayName("다음 페이지가 있으면 요청 개수만 반환하고 다음 커서를 생성한다.")
    void getReviewList_hasNext() {
        // given
        UUID contentId = content.getId();

        ReviewListCondition condition = new ReviewListCondition(
                null,
                null,
                2,
                SortDirection.DESCENDING,
                "rating"
        );

        Review secondReview = reviews.get(1);
        UUID secondReviewId = reviewResults.get(1).dto().id();

        given(secondReview.getId()).willReturn(secondReviewId);
        given(secondReview.getRating()).willReturn(3.0);

        // 다음 페이지 존재 여부 확인을 위해 limit + 1개 반환
        given(reviewRepository.getReviewsByContentId(contentId, condition))
                .willReturn(reviews.subList(0, 3));

        given(reviewRepository.countByContentId(contentId))
                .willReturn(4L);

        given(reviewMapper.toResult(reviews.get(0)))
                .willReturn(reviewResults.get(0));

        given(reviewMapper.toResult(secondReview))
                .willReturn(reviewResults.get(1));

        // when
        CursorResponse<ReviewResult> result = reviewReader.getReviews(contentId, condition);

        // then
        assertThat(result.data())
                .containsExactly(reviewResults.get(0), reviewResults.get(1));

        assertThat(result.hasNext()).isTrue();
        assertThat(result.nextCursor()).isEqualTo("3.0");
        assertThat(result.nextIdAfter()).isEqualTo(secondReviewId);
        assertThat(result.totalCount()).isEqualTo(4L);

        then(reviewMapper).should(never()).toResult(reviews.get(2));
    }

    @Test
    @DisplayName("마지막 페이지에서는 다음 커서를 생성하지 않는다.")
    void getReviewList_lastPage() {
        // given
        UUID contentId = content.getId();

        ReviewListCondition condition = new ReviewListCondition(
                "3.0",
                reviewResults.get(1).dto().id(),
                2,
                SortDirection.DESCENDING,
                "rating"
        );

        // 마지막 페이지 조회를 위해 남은 리뷰 2개 반환
        given(reviewRepository.getReviewsByContentId(contentId, condition))
                .willReturn(reviews.subList(2, 4));

        given(reviewRepository.countByContentId(contentId))
                .willReturn(4L);

        given(reviewMapper.toResult(reviews.get(2)))
                .willReturn(reviewResults.get(2));

        given(reviewMapper.toResult(reviews.get(3)))
                .willReturn(reviewResults.get(3));

        // when
        CursorResponse<ReviewResult> result =
                reviewReader.getReviews(contentId, condition);

        // then
        assertThat(result.data())
                .containsExactly(reviewResults.get(2), reviewResults.get(3));

        assertThat(result.hasNext()).isFalse();
        assertThat(result.nextCursor()).isNull();
        assertThat(result.nextIdAfter()).isNull();
        assertThat(result.totalCount()).isEqualTo(4L);
    }

}
