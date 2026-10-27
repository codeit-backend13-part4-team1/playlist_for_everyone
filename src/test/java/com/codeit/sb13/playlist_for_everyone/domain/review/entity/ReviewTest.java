package com.codeit.sb13.playlist_for_everyone.domain.review.entity;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Review 엔터티")
public class ReviewTest {

    @Test
    @DisplayName("리뷰 생성 시 평점과 내용이 정상적으로 설정된다")
    void createReview() {
        // given
        Review review = Review.builder()
                .text("재밌어요!")
                .rating(4.5)
                .build();

        // then
        assertThat(review.getText()).isEqualTo("재밌어요!");
        assertThat(review.getRating()).isEqualTo(4.5);
    }

}
