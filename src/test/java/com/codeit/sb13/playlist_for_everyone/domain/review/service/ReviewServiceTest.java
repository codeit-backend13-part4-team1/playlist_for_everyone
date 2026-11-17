package com.codeit.sb13.playlist_for_everyone.domain.review.service;

import com.codeit.sb13.playlist_for_everyone.domain.content.entity.Content;
import com.codeit.sb13.playlist_for_everyone.domain.content.entity.ContentType;
import com.codeit.sb13.playlist_for_everyone.domain.content.repository.ContentRepository;
import com.codeit.sb13.playlist_for_everyone.domain.review.dto.request.ReviewCreateRequest;
import com.codeit.sb13.playlist_for_everyone.domain.review.dto.response.ReviewDto;
import com.codeit.sb13.playlist_for_everyone.domain.review.entity.Review;
import com.codeit.sb13.playlist_for_everyone.domain.review.mapper.ReviewMapper;
import com.codeit.sb13.playlist_for_everyone.domain.review.repository.ReviewRepository;
import com.codeit.sb13.playlist_for_everyone.domain.user.entity.User;
import com.codeit.sb13.playlist_for_everyone.domain.user.mapper.UserDtoMapper;
import com.codeit.sb13.playlist_for_everyone.domain.user.repository.UserRepository;
import com.codeit.sb13.playlist_for_everyone.global.exception.content.ContentNotFoundException;
import com.codeit.sb13.playlist_for_everyone.global.exception.user.UserNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.mock;

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

    @Test
    @DisplayName("리뷰 생성 시 콘텐츠, 작성자, 텍스트, 평점이 정상적으로 저장된다.")
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

        given(reviewRepository.save(any(Review.class)))
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

        then(reviewRepository).should().save(captor.capture());

        Review savedReview = captor.getValue();

        assertThat(savedReview.getContent()).isEqualTo(content);
        assertThat(savedReview.getAuthor()).isEqualTo(author);
        assertThat(savedReview.getText()).isEqualTo("재미있는 드라마였어요.");
        assertThat(savedReview.getRating()).isEqualTo(4.5);
    }

    @Test
    @DisplayName("콘텐츠가 존재하지 않으면 리뷰 생성 시 예외가 발생한다.")
    void saveReviews_withoutContent() {
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
    void saveReviews_withoutAuthor() {
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

}
