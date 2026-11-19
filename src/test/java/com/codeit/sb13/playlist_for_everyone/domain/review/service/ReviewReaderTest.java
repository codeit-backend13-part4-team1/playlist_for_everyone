package com.codeit.sb13.playlist_for_everyone.domain.review.service;

import com.codeit.sb13.playlist_for_everyone.domain.content.entity.Content;
import com.codeit.sb13.playlist_for_everyone.domain.content.entity.ContentType;
import com.codeit.sb13.playlist_for_everyone.domain.review.dto.condition.ReviewListCondition;
import com.codeit.sb13.playlist_for_everyone.domain.review.dto.response.ReviewDto;
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

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
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

    @BeforeEach
    void setUp() {
        content = Content.createAdminContent(
                ContentType.tvSeries,
                "드라마1",
                "드라마설명1",
                null
        );

        ReflectionTestUtils.setField(content, "id", UUID.randomUUID());
    }

    /** 리뷰 목록 조회 테스트 */
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

        Review review1 = mock(Review.class);
        Review review2 = mock(Review.class);

        ReviewDto dto1 = mock(ReviewDto.class);
        ReviewDto dto2 = mock(ReviewDto.class);

        when(reviewRepository.getReviewsByContentId(contentId, condition))
                .thenReturn(List.of(review1, review2));

        when(reviewMapper.toDto(review1)).thenReturn(dto1);
        when(reviewMapper.toDto(review2)).thenReturn(dto2);

        // when
        CursorResponse<ReviewDto> result =
                reviewReader.getReviews(contentId, condition);

        // then
        assertThat(result).isNotNull();

        verify(reviewRepository)
                .getReviewsByContentId(contentId, condition);

        verify(reviewMapper).toDto(review1);
        verify(reviewMapper).toDto(review2);
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

        Review review1 = mock(Review.class);
        Review review2 = mock(Review.class);
        Review review3 = mock(Review.class);

        UUID review2Id = UUID.randomUUID();

        given(review2.getId()).willReturn(review2Id);
        given(review2.getRating()).willReturn(4.0);

        ReviewDto dto1 = mock(ReviewDto.class);
        ReviewDto dto2 = mock(ReviewDto.class);

        given(reviewRepository.getReviewsByContentId(contentId, condition))
                .willReturn(List.of(review1, review2, review3));

        given(reviewRepository.countByContentId(contentId))
                .willReturn(3L);

        given(reviewMapper.toDto(review1)).willReturn(dto1);
        given(reviewMapper.toDto(review2)).willReturn(dto2);

        // when
        CursorResponse<ReviewDto> result =
                reviewReader.getReviews(contentId, condition);

        // then
        assertThat(result.data()).containsExactly(dto1, dto2);
        assertThat(result.hasNext()).isTrue();
        assertThat(result.nextCursor()).isEqualTo("4.0");
        assertThat(result.nextIdAfter()).isEqualTo(review2Id);
        assertThat(result.totalCount()).isEqualTo(3L);

        then(reviewMapper).should(never()).toDto(review3);
    }

    @Test
    @DisplayName("마지막 페이지에서는 다음 커서를 생성하지 않는다.")
    void getReviewList_lastPage() {
        // given
        UUID contentId = content.getId();

        ReviewListCondition condition = new ReviewListCondition(
                null,
                null,
                2,
                SortDirection.DESCENDING,
                "rating"
        );

        Review review = mock(Review.class);
        ReviewDto dto = mock(ReviewDto.class);

        given(reviewRepository.getReviewsByContentId(contentId, condition))
                .willReturn(List.of(review));

        given(reviewRepository.countByContentId(contentId))
                .willReturn(1L);

        given(reviewMapper.toDto(review)).willReturn(dto);

        // when
        CursorResponse<ReviewDto> result =
                reviewReader.getReviews(contentId, condition);

        // then
        assertThat(result.data()).containsExactly(dto);
        assertThat(result.hasNext()).isFalse();
        assertThat(result.nextCursor()).isNull();
        assertThat(result.nextIdAfter()).isNull();
        assertThat(result.totalCount()).isEqualTo(1L);
    }


}
