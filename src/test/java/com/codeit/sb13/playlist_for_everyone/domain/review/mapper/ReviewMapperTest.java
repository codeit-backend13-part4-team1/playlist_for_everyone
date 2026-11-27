package com.codeit.sb13.playlist_for_everyone.domain.review.mapper;

import com.codeit.sb13.playlist_for_everyone.domain.content.entity.Content;
import com.codeit.sb13.playlist_for_everyone.domain.review.dto.response.ReviewAuthorDto;
import com.codeit.sb13.playlist_for_everyone.domain.review.dto.response.ReviewDto;
import com.codeit.sb13.playlist_for_everyone.domain.review.dto.response.ReviewResult;
import com.codeit.sb13.playlist_for_everyone.domain.review.entity.Review;
import com.codeit.sb13.playlist_for_everyone.domain.user.entity.User;
import com.codeit.sb13.playlist_for_everyone.global.file.entity.FileInfo;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

@DisplayName("리뷰 매퍼 테스트")
public class ReviewMapperTest {

    private final ReviewMapper reviewMapper = new ReviewMapper();

    @Test
    @DisplayName("리뷰 엔터티를 ReviewDto로 변환한다.")
    void toDto() {
        // given
        UUID reviewId = UUID.randomUUID();
        UUID contentId = UUID.randomUUID();
        UUID authorId = UUID.randomUUID();

        Content content = mock(Content.class);
        User author = mock(User.class);
        Review review = mock(Review.class);

        given(review.getId()).willReturn(reviewId);
        given(review.getContent()).willReturn(content);
        given(content.getId()).willReturn(contentId);
        given(review.getAuthor()).willReturn(author);
        given(author.getId()).willReturn(authorId);
        given(author.getName()).willReturn("테스트 작성자");
        given(review.getText()).willReturn("재미있는 드라마였어요.");
        given(review.getRating()).willReturn(4.5);

        // when
        ReviewDto dto = reviewMapper.toDto(review);

        // then
        assertThat(dto.id()).isEqualTo(reviewId);
        assertThat(dto.contentId()).isEqualTo(contentId);
        assertThat(dto.text()).isEqualTo("재미있는 드라마였어요.");
        assertThat(dto.rating()).isEqualTo(4.5);

        // 작성자 정보 검증
        assertThat(dto.author().userId()).isEqualTo(authorId);
        assertThat(dto.author().name()).isEqualTo("테스트 작성자");
        assertThat(dto.author().profileImageUrl()).isNull();
    }

    @Test
    @DisplayName("리뷰 엔터티를 ReviewResult로 변환한다.")
    void toResult() {
        // given
        UUID reviewId = UUID.randomUUID();
        UUID contentId = UUID.randomUUID();
        UUID authorId = UUID.randomUUID();

        Content content = mock(Content.class);
        User author = mock(User.class);
        Review review = mock(Review.class);
        FileInfo profileImage = mock(FileInfo.class);

        given(review.getId()).willReturn(reviewId);
        given(review.getContent()).willReturn(content);
        given(content.getId()).willReturn(contentId);
        given(review.getAuthor()).willReturn(author);
        given(author.getId()).willReturn(authorId);
        given(author.getName()).willReturn("테스트 작성자");
        given(author.getProfileImage()).willReturn(profileImage);
        given(profileImage.getKey()).willReturn("profiles/test-image.png");
        given(review.getText()).willReturn("재미있는 드라마였어요.");
        given(review.getRating()).willReturn(4.5);

        // when
        ReviewResult result = reviewMapper.toResult(review);

        // then
        assertThat(result.profileImageKey()).isEqualTo("profiles/test-image.png");

        ReviewDto dto = result.dto();

        assertThat(dto.id()).isEqualTo(reviewId);
        assertThat(dto.contentId()).isEqualTo(contentId);
        assertThat(dto.author().userId()).isEqualTo(authorId);
        assertThat(dto.author().name()).isEqualTo("테스트 작성자");
        assertThat(dto.author().profileImageUrl()).isNull();
        assertThat(dto.text()).isEqualTo("재미있는 드라마였어요.");
        assertThat(dto.rating()).isEqualTo(4.5);
    }

    @Test
    @DisplayName("작성자의 프로필 이미지가 없으면 이미지 키를 null로 반환한다.")
    void toResult_withoutProfileImage() {
        // given
        Content content = mock(Content.class);
        User author = mock(User.class);
        Review review = mock(Review.class);

        given(review.getContent()).willReturn(content);
        given(review.getAuthor()).willReturn(author);
        given(author.getProfileImage()).willReturn(null);

        // when
        ReviewResult result = reviewMapper.toResult(review);

        // then
        assertThat(result.profileImageKey()).isNull();
        assertThat(result.dto().author().profileImageUrl()).isNull();
    }

    @Test
    @DisplayName("ReviewResult를 profileImageUrl을 포함한 dto로 변환한다.")
    void toDto_withProfileUrl() {
        // given
        UUID reviewId = UUID.randomUUID();
        UUID contentId = UUID.randomUUID();
        UUID authorId = UUID.randomUUID();

        ReviewAuthorDto authorDto = new ReviewAuthorDto(
                authorId,
                "테스트 작성자",
                null
        );

        ReviewDto dto = new ReviewDto(
                reviewId,
                contentId,
                authorDto,
                "재미있는 드라마였어요.",
                4.5
        );

        String profileImageUrl = "https://example.com/profile.png";

        // when
        ReviewDto result = reviewMapper.withProfileImageUrl(dto, profileImageUrl);

        // then
        assertThat(result.id()).isEqualTo(reviewId);
        assertThat(result.contentId()).isEqualTo(contentId);
        assertThat(result.author().userId()).isEqualTo(authorId);
        assertThat(result.author().name()).isEqualTo("테스트 작성자");
        assertThat(result.author().profileImageUrl()).isEqualTo(profileImageUrl);
        assertThat(result.text()).isEqualTo("재미있는 드라마였어요.");
        assertThat(result.rating()).isEqualTo(4.5);
    }

}
