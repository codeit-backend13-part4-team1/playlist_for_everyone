package com.codeit.sb13.playlist_for_everyone.domain.review.mapper;

import com.codeit.sb13.playlist_for_everyone.domain.content.entity.Content;
import com.codeit.sb13.playlist_for_everyone.domain.review.dto.response.ReviewDto;
import com.codeit.sb13.playlist_for_everyone.domain.review.entity.Review;
import com.codeit.sb13.playlist_for_everyone.domain.user.entity.User;
import com.codeit.sb13.playlist_for_everyone.domain.user.mapper.UserDtoMapper;
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
        assertThat(dto.authorDto().authorId()).isEqualTo(authorId);
        assertThat(dto.authorDto().nickname()).isEqualTo("테스트 작성자");
        assertThat(dto.authorDto().profileImageUrl()).isNull();
    }

}
