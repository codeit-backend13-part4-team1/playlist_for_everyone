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

    @Test
    @DisplayName("리뷰의 텍스트와 평점을 수정한다.")
    void updateReview() {
        // given
        Review review = Review.builder()
                .text("재밌어요!")
                .rating(4.5)
                .build();

        // when
        review.update("수정된 리뷰입니다.", 4.0);

        // then
        assertThat(review.getText()).isEqualTo("수정된 리뷰입니다.");
        assertThat(review.getRating()).isEqualTo(4.0);
    }

    @Test
    @DisplayName("텍스트가 null이면 기존 내용을 유지하고 평점만 수정한다.")
    void updateReview_nullText() {
        // given
        Review review = Review.builder()
                .text("재밌어요!")
                .rating(4.5)
                .build();

        // when
        review.update(null, 3.0);

        // then
        assertThat(review.getText()).isEqualTo("재밌어요!");
        assertThat(review.getRating()).isEqualTo(3.0);
    }

    @Test
    @DisplayName("평점이 null이면 기존 평점을 유지하고 텍스트만 수정한다.")
    void updateReview_nullRating() {
        // given
        Review review = Review.builder()
                .text("재밌어요!")
                .rating(4.5)
                .build();

        // when
        review.update("수정된 리뷰입니다.", null);

        // then
        assertThat(review.getText()).isEqualTo("수정된 리뷰입니다.");
        assertThat(review.getRating()).isEqualTo(4.5);
    }

}
