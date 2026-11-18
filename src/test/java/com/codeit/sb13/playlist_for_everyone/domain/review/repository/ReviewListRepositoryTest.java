package com.codeit.sb13.playlist_for_everyone.domain.review.repository;


import com.codeit.sb13.playlist_for_everyone.domain.content.entity.Content;
import com.codeit.sb13.playlist_for_everyone.domain.content.entity.ContentType;
import com.codeit.sb13.playlist_for_everyone.domain.review.dto.condition.ReviewListCondition;
import com.codeit.sb13.playlist_for_everyone.domain.review.entity.Review;
import com.codeit.sb13.playlist_for_everyone.domain.user.entity.Role;
import com.codeit.sb13.playlist_for_everyone.domain.user.entity.User;
import com.codeit.sb13.playlist_for_everyone.global.config.JpaAuditingConfig;
import com.codeit.sb13.playlist_for_everyone.global.config.QueryDslConfig;
import com.codeit.sb13.playlist_for_everyone.global.domain.SortDirection;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.context.annotation.Import;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@Import({
        QueryDslConfig.class,
        JpaAuditingConfig.class
})

@DisplayName("리뷰 리스트 테스트")
public class ReviewListRepositoryTest {

    @Autowired
    private ReviewRepository reviewRepository;

    @Autowired
    private TestEntityManager em;

    private Content content1;
    private Content content2;
    private Content content3;

    private User author1;
    private User author2;
    private User author3;

    @BeforeEach
    void setup() {
        // 콘텐츠 3개
        content1 = createContent("영화1");
        content2 = createContent("영화2");
        content3 = createContent("영화3");

        // 사용자 3명
        author1 = createAuthor(1);
        author2 = createAuthor(2);
        author3 = createAuthor(3);

        // 콘텐츠1 리뷰
        createReview(content1, author1, 5.0, "정말 재미있어요");
        createReview(content1, author2, 4.0, "재미있게 봤어요");
        createReview(content1, author3, 3.0, "보통이에요");

        // 콘텐츠2 리뷰
        createReview(content2, author1, 4.0, "추천합니다");
        createReview(content2, author2, 5.0, "최고예요");
        createReview(content2, author3, 2.0, "아쉬웠어요");

        // 콘텐츠3 리뷰
        createReview(content3, author1, 3.0, "괜찮아요");
        createReview(content3, author2, 4.0, "재미있어요");
        createReview(content3, author3, 5.0, "또 보고 싶어요");

        em.flush();
        em.clear();
    }

    @Test
    @DisplayName("콘텐츠별 리뷰 리스트를 조회할 수 있다.")
    void getReviewList_byContentId() {
        // given
        UUID contentId = content1.getId();

        ReviewListCondition condition = new ReviewListCondition(
                null,
                null,
                10,
                SortDirection.DESCENDING,
                "createdAt"
        );

        // when
        List<Review> reviews = reviewRepository.getReviewsByContentId(contentId, condition);

        // then
        assertThat(reviews).hasSize(3);

        assertThat(reviews)
                .extracting(r -> r.getContent().getId())
                .containsOnly(contentId);

        assertThat(reviews)
                .extracting(Review::getText)
                .containsExactlyInAnyOrder(
                        "정말 재미있어요",
                        "재미있게 봤어요",
                        "보통이에요"
                );
    }

    @Test
    @DisplayName("리뷰 생성일 오름차순으로 조회할 수 있다.")
    void getReviewList_orderByCreatedAt() {
        // given
        UUID contentId = content2.getId();

        ReviewListCondition condition = new ReviewListCondition(
                null,
                null,
                10,
                SortDirection.ASCENDING,
                "createdAt"
        );

        // when
        List<Review> reviews = reviewRepository.getReviewsByContentId(contentId, condition);

        // then
        assertThat(reviews).hasSize(3);

        assertThat(reviews)
                .extracting(Review::getCreatedAt)
                .isSorted();
    }

    @Test
    @DisplayName("리뷰를 평점 높은 순으로 조회할 수 있다.")
    void getReviewList_sortByRatingDesc() {
        // given
        UUID contentId = content3.getId();

        ReviewListCondition condition = new ReviewListCondition(
                null,
                null,
                10,
                SortDirection.DESCENDING,
                "rating"
        );

        // when
        List<Review> reviews =
                reviewRepository.getReviewsByContentId(contentId, condition);

        // then
        assertThat(reviews)
                .extracting(Review::getRating)
                .containsExactly(5.0, 4.0, 3.0);
    }

    @Test
    @DisplayName("요청한 개수보다 한 개 더 조회하여 다음 페이지 존재 여부를 확인할 수 있다.")
    void getReviewList_limit() {
        // given
        UUID contentId = content1.getId();

        ReviewListCondition condition = new ReviewListCondition(
                null,
                null,
                2,
                SortDirection.DESCENDING,
                "createdAt"
        );

        // when
        List<Review> reviews =
                reviewRepository.getReviewsByContentId(contentId, condition);

        // then
        assertThat(reviews).hasSize(3);
    }

    private Content createContent(String title) {
        return em.persist(
                Content.createAdminContent(
                        ContentType.movie,
                        title,
                        title + " 설명",
                        null
                )
        );
    }

    private User createAuthor(int number) {
        return em.persist(
                User.builder()
                        .email("test" + number + "@example.com")
                        .name("테스트 작성자" + number)
                        .password("encodedPassword")
                        .role(Role.USER)
                        .build()
        );
    }

    private void createReview(
            Content content,
            User author,
            double rating,
            String text
    ) {
        em.persist(Review.builder()
                .content(content)
                .author(author)
                .rating(rating)
                .text(text)
                .build());
    }

}
