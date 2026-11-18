package com.codeit.sb13.playlist_for_everyone.domain.review.service;

import com.codeit.sb13.playlist_for_everyone.domain.content.entity.Content;
import com.codeit.sb13.playlist_for_everyone.domain.content.entity.ContentType;
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
import com.codeit.sb13.playlist_for_everyone.domain.user.mapper.UserDtoMapper;
import com.codeit.sb13.playlist_for_everyone.domain.user.repository.UserRepository;
import com.codeit.sb13.playlist_for_everyone.global.domain.SortDirection;
import com.codeit.sb13.playlist_for_everyone.global.dto.response.CursorResponse;
import com.codeit.sb13.playlist_for_everyone.global.exception.content.ContentNotFoundException;
import com.codeit.sb13.playlist_for_everyone.global.exception.review.ReviewAlreadyExistsException;
import com.codeit.sb13.playlist_for_everyone.global.exception.review.ReviewNotFoundException;
import com.codeit.sb13.playlist_for_everyone.global.exception.user.UserNotFoundException;
import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.util.ReflectionTestUtils;

import org.springframework.security.access.AccessDeniedException;

import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("리뷰 서비스 테스트")
public class ReviewServiceTest {

    @Mock
    private ReviewRepository reviewRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private ContentRepository contentRepository;

    @Mock
    private ReviewMapper reviewMapper;

    @Mock
    private UserDtoMapper userMapper;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private ReviewService reviewService;

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

    /** 리뷰 생성 테스트 */
    @Test
    @DisplayName("리뷰 생성 시 콘텐츠, 작성자, 텍스트, 평점이 저장되고 캐시 무효화 이벤트를 발행한다.")
    void saveReview() {
        // given
        UUID authorId = UUID.randomUUID();

        ReviewCreateRequest request = new ReviewCreateRequest(
                content.getId(),
                "재미있는 드라마였어요.",
                4.5
        );

        User author = mock(User.class);

        given(contentRepository.findById(content.getId())).willReturn(Optional.of(content));
        given(userRepository.findById(authorId)).willReturn(Optional.of(author));

        given(reviewRepository.saveAndFlush(any(Review.class)))
                .willAnswer(invocation -> invocation.getArgument(0));

        given(reviewMapper.toDto(any(Review.class)))
                .willAnswer(invocation -> {
                    Review review = invocation.getArgument(0);

                    return new ReviewDto(
                            UUID.randomUUID(),
                            content.getId(),
                            userMapper.toDto(author),
                            review.getText(),
                            review.getRating()
                    );
                });

        // when
        reviewService.save(request, authorId);

        // then
        ArgumentCaptor<Review> captor = ArgumentCaptor.forClass(Review.class);

        then(reviewRepository).should().saveAndFlush(captor.capture());

        Review savedReview = captor.getValue();

        assertThat(savedReview.getContent()).isEqualTo(content);
        assertThat(savedReview.getAuthor()).isEqualTo(author);
        assertThat(savedReview.getText()).isEqualTo("재미있는 드라마였어요.");
        assertThat(savedReview.getRating()).isEqualTo(4.5);

        // 콘텐츠 검색 캐시 무효화
        then(eventPublisher).should()
                .publishEvent(any(ContentSearchCacheEvictEvent.class));
    }

    @Test
    @DisplayName("작성자가 해당 콘텐츠에 이미 작성한 리뷰가 존재하면 409를 반환한다.")
    void saveReview_alreadyExists() {
        // given
        UUID authorId = UUID.randomUUID();

        ReviewCreateRequest request = new ReviewCreateRequest(
                content.getId(),
                "재미있는 영화였어요.",
                4.5
        );

        User author = mock(User.class);

        DataIntegrityViolationException exception = new DataIntegrityViolationException(
                        "중복 리뷰",
                        new ConstraintViolationException(
                                "Unique constraint violation",
                                new SQLException("Duplicate key"),
                                "uk_review_content_author"
                        )
                );

        given(contentRepository.findById(content.getId())).willReturn(Optional.of(content));
        given(userRepository.findById(authorId)).willReturn(Optional.of(author));
        given(reviewRepository.saveAndFlush(any(Review.class))).willThrow(exception);

        // when & then
        assertThatThrownBy(() -> reviewService.save(request, authorId))
                .isInstanceOf(ReviewAlreadyExistsException.class);

        then(eventPublisher).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("콘텐츠가 존재하지 않으면 리뷰 생성 시 예외가 발생한다.")
    void saveReviews_notFoundContent() {
        // given
        UUID authorId = UUID.randomUUID();

        ReviewCreateRequest request = new ReviewCreateRequest(
                content.getId(),
                "재미있는 영화였어요.",
                4.5
        );

        given(contentRepository.findById(content.getId())).willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> reviewService.save(request, authorId))
                .isInstanceOf(ContentNotFoundException.class);

        then(reviewRepository).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("유저가 존재하지 않으면 리뷰 생성 시 예외가 발생한다.")
    void saveReviews_notFoundAuthor() {
        // given
        UUID authorId = UUID.randomUUID();

        ReviewCreateRequest request = new ReviewCreateRequest(
                content.getId(),
                "재미있는 영화였어요.",
                4.5
        );

        given(contentRepository.findById(content.getId())).willReturn(Optional.of(content));
        given(userRepository.findById(authorId)).willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> reviewService.save(request, authorId))
                .isInstanceOf(UserNotFoundException.class);

        then(userRepository).should().findById(authorId);
        then(reviewRepository).shouldHaveNoInteractions();
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
                reviewService.getReviews(contentId, condition);

        // then
        assertThat(result).isNotNull();

        verify(reviewRepository)
                .getReviewsByContentId(contentId, condition);

        verify(reviewMapper).toDto(review1);
        verify(reviewMapper).toDto(review2);
    }

    /** 리뷰 수정 테스트 */
    @Test
    @DisplayName("리뷰 수정 시 텍스트와 평점이 정상적으로 변경되고 캐시 무효화 이벤트를 발행한다.")
    void updateReview() {
        // given
        UUID reviewId = UUID.randomUUID();
        UUID authorId = UUID.randomUUID();

        User author = mock(User.class);
        given(author.getId()).willReturn(authorId);

        Review review = Review.builder()
                .content(content)
                .author(author)
                .text("기존 리뷰입니다.")
                .rating(3.0)
                .build();

        ReviewUpdateRequest request = new ReviewUpdateRequest(
                "재미있는 영화였어요.",
                4.5
        );

        given(reviewRepository.findById(reviewId)).willReturn(Optional.of(review));

        // when
        reviewService.update(request, reviewId, authorId);

        // then
        assertThat(review.getText()).isEqualTo("재미있는 영화였어요.");
        assertThat(review.getRating()).isEqualTo(4.5);

        then(reviewMapper).should().toDto(review);

        then(eventPublisher).should()
                .publishEvent(any(ContentSearchCacheEvictEvent.class));
    }

    @Test
    @DisplayName("리뷰 작성자가 아니면 수정 시 예외가 발생한다.")
    void updateReview_withoutPermission() {
        // given
        UUID reviewId = UUID.randomUUID();
        UUID authorId = UUID.randomUUID();
        UUID otherUserId = UUID.randomUUID();

        User author = mock(User.class);
        given(author.getId()).willReturn(authorId);

        Review review = Review.builder()
                .content(content)
                .author(author)
                .text("기존 리뷰입니다.")
                .rating(3.0)
                .build();

        ReviewUpdateRequest request = new ReviewUpdateRequest(
                "수정된 리뷰입니다.",
                4.5
        );

        given(reviewRepository.findById(reviewId)).willReturn(Optional.of(review));

        // when & then
        assertThatThrownBy(() -> reviewService.update(request, reviewId, otherUserId))
                .isInstanceOf(AccessDeniedException.class);

        assertThat(review.getText()).isEqualTo("기존 리뷰입니다.");
        assertThat(review.getRating()).isEqualTo(3.0);
    }

    @Test
    @DisplayName("리뷰가 존재하지 않으면 수정 시 예외가 발생한다.")
    void updateReview_withoutReview() {
        // given
        UUID reviewId = UUID.randomUUID();
        UUID authorId = UUID.randomUUID();

        ReviewUpdateRequest request = new ReviewUpdateRequest(
                "수정된 리뷰입니다.",
                4.5
        );

        given(reviewRepository.findById(reviewId)).willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> reviewService.update(request, reviewId, authorId))
                .isInstanceOf(ReviewNotFoundException.class);

        then(reviewMapper).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("리뷰 수정 시 평점이 변경되지 않으면 캐시 무효화 이벤트를 발행하지 않는다.")
    void updateReview_ratingUnchanged() {
        // given
        UUID reviewId = UUID.randomUUID();
        UUID authorId = UUID.randomUUID();

        User author = mock(User.class);
        given(author.getId()).willReturn(authorId);

        Review review = Review.builder()
                .content(content)
                .author(author)
                .text("기존 리뷰입니다.")
                .rating(3.0)
                .build();

        ReviewUpdateRequest request = new ReviewUpdateRequest(
                "수정된 리뷰입니다.",
                null
        );

        given(reviewRepository.findById(reviewId)).willReturn(Optional.of(review));

        // when
        reviewService.update(request, reviewId, authorId);

        // then
        assertThat(review.getText()).isEqualTo("수정된 리뷰입니다.");
        assertThat(review.getRating()).isEqualTo(3.0);

        then(eventPublisher).shouldHaveNoInteractions();
    }

    /** 리뷰 삭제 테스트 */
    @Test
    @DisplayName("리뷰 작성자가 삭제 요청 시 정상적으로 삭제된다.")
    void deleteReview() {
        // given
        UUID reviewId = UUID.randomUUID();
        UUID authorId = UUID.randomUUID();

        User author = mock(User.class);
        given(author.getId()).willReturn(authorId);

        Review review = Review.builder()
                .content(content)
                .author(author)
                .text("삭제될 리뷰입니다.")
                .rating(3.0)
                .build();

        given(reviewRepository.findById(reviewId)).willReturn(Optional.of(review));

        // when
        reviewService.delete(reviewId, authorId);

        // then
        then(reviewRepository).should().delete(review);
        then(eventPublisher).should()
                .publishEvent(any(ContentSearchCacheEvictEvent.class));
    }

    @Test
    @DisplayName("리뷰 작성자가 아니면 삭제 시 예외가 발생한다.")
    void deleteReview_withoutPermission() {
        // given
        UUID reviewId = UUID.randomUUID();
        UUID authorId = UUID.randomUUID();
        UUID otherUserId = UUID.randomUUID();

        User author = mock(User.class);
        given(author.getId()).willReturn(authorId);

        Review review = Review.builder()
                .content(content)
                .author(author)
                .text("기존 리뷰입니다.")
                .rating(3.0)
                .build();

        given(reviewRepository.findById(reviewId))
                .willReturn(Optional.of(review));

        // when & then
        assertThatThrownBy(() -> reviewService.delete(reviewId, otherUserId))
                .isInstanceOf(AccessDeniedException.class);

        then(reviewRepository).should(never()).delete(any(Review.class));
        then(eventPublisher).shouldHaveNoInteractions();
    }

}
