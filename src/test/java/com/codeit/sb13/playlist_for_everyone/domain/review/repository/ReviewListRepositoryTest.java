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
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.dao.InvalidDataAccessApiUsageException;

import java.util.List;
import java.util.UUID;

import static com.codeit.sb13.playlist_for_everyone.domain.review.entity.QReview.review;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@Import({
        QueryDslConfig.class,
        JpaAuditingConfig.class
})

@DisplayName("리뷰 리스트 레포지토리 테스트")
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
    private User author4;

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
        author4 = createAuthor(4);

        // 콘텐츠1 리뷰
        createReview(content1, author1, 5.0, "정말 재미있어요");
        createReview(content1, author2, 4.0, "재미있게 봤어요");
        createReview(content1, author3, 4.0, "볼만해요");
        createReview(content1, author4, 3.0, "그냥그래요");

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
        assertThat(reviews).hasSize(4);

        assertThat(reviews)
                .extracting(r -> r.getContent().getId())
                .containsOnly(contentId);

        assertThat(reviews)
                .extracting(Review::getText)
                .containsExactlyInAnyOrder(
                        "정말 재미있어요",
                        "재미있게 봤어요",
                        "볼만해요",
                        "그냥그래요"
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

    @Test
    @DisplayName("평점이 같은 리뷰도 커서 페이지네이션에서 누락 없이 조회할 수 있다.")
    void getReviewList_cursorByRating() {
        // given
        UUID contentId = content1.getId();

        ReviewListCondition firstCondition = new ReviewListCondition(
                null,
                null,
                2,
                SortDirection.DESCENDING,
                "rating"
        );

        List<Review> firstPage =
                reviewRepository.getReviewsByContentId(contentId, firstCondition);

        // 실제 페이지 크기인 2개 중 마지막 리뷰
        Review lastReview = firstPage.get(1);

        ReviewListCondition nextCondition = new ReviewListCondition(
                String.valueOf(lastReview.getRating()),
                lastReview.getId(),
                2,
                SortDirection.DESCENDING,
                "rating"
        );

        // when
        List<Review> nextPage =
                reviewRepository.getReviewsByContentId(contentId, nextCondition);

        // then
        assertThat(nextPage)
                .extracting(Review::getId)
                .doesNotContain(
                        firstPage.get(0).getId(),
                        firstPage.get(1).getId()
                );

        assertThat(nextPage.get(0).getAuthor().getId())
                .isIn(author2.getId(), author3.getId());

        assertThat(nextPage.get(0).getAuthor().getId())
                .isNotEqualTo(lastReview.getAuthor().getId());

        assertThat(nextPage.get(1).getAuthor().getId())
                .isEqualTo(author4.getId());
    }

    @Test
    @DisplayName("생성일 커서 이후의 리뷰를 조회할 수 있다.")
    void getReviewList_cursorByCreatedAt() {
        // given
        UUID contentId = content2.getId();

        ReviewListCondition firstCondition = new ReviewListCondition(
                null,
                null,
                2,
                SortDirection.ASCENDING,
                "createdAt"
        );

        List<Review> firstPage =
                reviewRepository.getReviewsByContentId(contentId, firstCondition);

        // 실제 페이지의 마지막 리뷰
        Review lastReview = firstPage.get(1);

        ReviewListCondition nextCondition = new ReviewListCondition(
                lastReview.getCreatedAt().toString(),
                lastReview.getId(),
                2,
                SortDirection.ASCENDING,
                "createdAt"
        );

        // when
        List<Review> nextPage =
                reviewRepository.getReviewsByContentId(contentId, nextCondition);

        // then
        assertThat(nextPage).hasSize(1);

        assertThat(nextPage.get(0).getId())
                .isEqualTo(firstPage.get(2).getId());
    }

    @Test
    @DisplayName("cursor와 idAfter가 함께 전달되지 않으면 예외가 발생한다.")
    void getReviewList_invalidCursor() {
        // given
        ReviewListCondition condition = new ReviewListCondition(
                "4.0",
                null,
                2,
                SortDirection.DESCENDING,
                "rating"
        );

        // when & then
        assertThatThrownBy(() ->
                reviewRepository.getReviewsByContentId(
                        content1.getId(),
                        condition
                )
        )
                .isInstanceOf(InvalidDataAccessApiUsageException.class)
                .hasMessageContaining("cursor와 idAfter는 함께 전달해야 합니다.");
    }

    @Test
    @DisplayName("리뷰 목록 조회 시 작성자와 프로필 이미지로 인한 추가 쿼리가 발생하지 않는다.")
    void getReviews_withoutNPlusOne() {
        // given
        ReviewListCondition condition = new ReviewListCondition(
                null,
                null,
                10,
                SortDirection.DESCENDING,
                "createdAt"
        );

        // 기존 setUp에서 서로 다른 작성자의 리뷰가 저장되어 있다고 가정
        em.flush();
        em.clear();

        SessionFactory sessionFactory = em
                .getEntityManager()
                .getEntityManagerFactory()
                .unwrap(SessionFactory.class);

        Statistics statistics = sessionFactory.getStatistics();
        boolean previousEnabled = statistics.isStatisticsEnabled();

        statistics.setStatisticsEnabled(true);

        try {
            statistics.clear();

            // when
            List<Review> results =
                    reviewRepository.getReviewsByContentId(content1.getId(), condition);

            // 작성자와 프로필 이미지 접근 시 추가 쿼리가 발생하는지 확인
            for (Review review : results) {
                review.getAuthor().getName();

                if (review.getAuthor().getProfileImage() != null) {
                    review.getAuthor().getProfileImage().getKey();
                }
            }

            // then
            assertThat(results).hasSize(4);
            assertThat(statistics.getPrepareStatementCount()).isEqualTo(1);
        } finally {
            statistics.setStatisticsEnabled(previousEnabled);
        }
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
