package com.codeit.sb13.playlist_for_everyone.domain.review.service;

import com.codeit.sb13.playlist_for_everyone.domain.review.dto.condition.ReviewListCondition;
import com.codeit.sb13.playlist_for_everyone.domain.review.dto.request.ReviewCreateRequest;
import com.codeit.sb13.playlist_for_everyone.domain.review.dto.request.ReviewUpdateRequest;
import com.codeit.sb13.playlist_for_everyone.domain.review.dto.response.ReviewAuthorDto;
import com.codeit.sb13.playlist_for_everyone.domain.review.dto.response.ReviewDto;
import com.codeit.sb13.playlist_for_everyone.domain.review.dto.response.ReviewResult;
import com.codeit.sb13.playlist_for_everyone.domain.review.mapper.ReviewMapper;
import com.codeit.sb13.playlist_for_everyone.global.domain.SortDirection;
import com.codeit.sb13.playlist_for_everyone.global.dto.response.CursorResponse;
import com.codeit.sb13.playlist_for_everyone.global.file.service.FileService;
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

    @Mock
    private FileService fileService;

    @Mock
    private ReviewMapper reviewMapper;

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

        CursorResponse<ReviewResult> result = new CursorResponse<>(
                List.of(),
                null,
                null,
                false,
                0L,
                "createdAt",
                SortDirection.DESCENDING
        );

        given(reviewReader.getReviews(contentId, condition)).willReturn(result);

        // when
        CursorResponse<ReviewDto> response = reviewService.getReviews(contentId, condition);

        // then
        assertThat(response.data()).isEmpty();
        assertThat(response.nextCursor()).isNull();
        assertThat(response.nextIdAfter()).isNull();
        assertThat(response.hasNext()).isFalse();
        assertThat(response.totalCount()).isZero();
        assertThat(response.sortBy()).isEqualTo("createdAt");
        assertThat(response.sortDirection()).isEqualTo(SortDirection.DESCENDING);

        then(reviewReader).should().getReviews(contentId, condition);
    }

    @Test
    @DisplayName("리뷰 목록 조회 시 프로필 이미지 URL을 생성하여 ReviewDto로 반환한다.")
    void getReviewList_withProfileImageUrl() {
        // given
        UUID contentId = UUID.randomUUID();
        UUID authorId1 = UUID.randomUUID();
        UUID authorId2 = UUID.randomUUID();

        ReviewListCondition condition = new ReviewListCondition(
                null,
                null,
                10,
                SortDirection.DESCENDING,
                "createdAt"
        );

        ReviewDto dto1 = new ReviewDto(
                UUID.randomUUID(),
                contentId,
                new ReviewAuthorDto(authorId1, "작성자1", null),
                "재미있는 드라마였어요.",
                4.5
        );

        ReviewDto dto2 = new ReviewDto(
                UUID.randomUUID(),
                contentId,
                new ReviewAuthorDto(authorId2, "작성자2", null),
                "괜찮은 드라마였어요.",
                4.0
        );

        ReviewResult result1 = new ReviewResult(dto1, "profiles/author1.png");
        ReviewResult result2 = new ReviewResult(dto2, "profiles/author2.png");

        CursorResponse<ReviewResult> readerResponse = new CursorResponse<>(
                List.of(result1, result2),
                null,
                null,
                false,
                2L,
                "createdAt",
                SortDirection.DESCENDING
        );

        String url1 = "https://example.com/author1.png";
        String url2 = "https://example.com/author2.png";

        given(reviewReader.getReviews(contentId, condition)).willReturn(readerResponse);

        given(fileService.getFileUrlByKey("profiles/author1.png")).willReturn(url1);
        given(fileService.getFileUrlByKey("profiles/author2.png")).willReturn(url2);

        given(reviewMapper.withProfileImageUrl(dto1, url1))
                .willReturn(new ReviewDto(
                        dto1.id(),
                        dto1.contentId(),
                        new ReviewAuthorDto(authorId1, "작성자1", url1),
                        dto1.text(),
                        dto1.rating()
                ));

        given(reviewMapper.withProfileImageUrl(dto2, url2))
                .willReturn(new ReviewDto(
                        dto2.id(),
                        dto2.contentId(),
                        new ReviewAuthorDto(authorId2, "작성자2", url2),
                        dto2.text(),
                        dto2.rating()
                ));

        // when
        CursorResponse<ReviewDto> response = reviewService.getReviews(contentId, condition);

        // then
        assertThat(response.data()).hasSize(2);
        assertThat(response.data().get(0).author().profileImageUrl()).isEqualTo(url1);
        assertThat(response.data().get(1).author().profileImageUrl()).isEqualTo(url2);
        assertThat(response.totalCount()).isEqualTo(2L);
        assertThat(response.hasNext()).isFalse();
        then(reviewReader).should().getReviews(contentId, condition);
        then(fileService).should().getFileUrlByKey("profiles/author1.png");
        then(fileService).should().getFileUrlByKey("profiles/author2.png");
        then(reviewMapper).should().withProfileImageUrl(dto1, url1);
        then(reviewMapper).should().withProfileImageUrl(dto2, url2);
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
