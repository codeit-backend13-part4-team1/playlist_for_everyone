package com.codeit.sb13.playlist_for_everyone.domain.review.repository;

import com.codeit.sb13.playlist_for_everyone.domain.content.entity.Content;
import com.codeit.sb13.playlist_for_everyone.domain.content.entity.ContentType;
import com.codeit.sb13.playlist_for_everyone.domain.review.entity.Review;
import com.codeit.sb13.playlist_for_everyone.domain.user.entity.Role;
import com.codeit.sb13.playlist_for_everyone.domain.user.entity.User;
import com.codeit.sb13.playlist_for_everyone.global.config.JpaAuditingConfig;
import com.codeit.sb13.playlist_for_everyone.global.config.QueryDslConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@Import({
        QueryDslConfig.class,
        JpaAuditingConfig.class
})
@DisplayName("Review 레포지토리")
public class ReviewRepositoryTest {

    @Autowired
    private ReviewRepository reviewRepository;

    @Autowired
    private TestEntityManager em;

    private Content createAndPersistContent(int number) {
        Content content = Content.createAdminContent(
                ContentType.movie,
                "영화 " + number,
                "영화 " + number + " 입니다",
                "https://example.com/movie" + number + ".png"
        );

        return em.persistFlushFind(content);
    }

    private User createAuthor(int number) {
        User author = User.builder()
                .email("test" + number + "@example.com")
                .name("테스트 사용자" + number)
                .password("encodedPassword")
                .role(Role.USER)
                .build();

        return em.persistFlushFind(author);
    }

    private Review createReview(Content content, User author, String text, double rating) {
        return Review.builder()
                .content(content)
                .author(author)
                .text(text)
                .rating(rating)
                .build();
    }

    @Test
    @DisplayName("동일한 콘텐츠에 동일한 사용자가 리뷰를 중복 등록할 수 없다")
    void duplicate_review() {
        // given
        Content content = createAndPersistContent(1);
        User author = createAuthor(1);

        Review review1 = createReview(content, author, "재밌어요!", 4.5);
        Review review2 = createReview(content, author, "정말 좋아요!", 5.0);

        // when
        reviewRepository.saveAndFlush(review1);

        // then
        assertThatThrownBy(() ->
                reviewRepository.saveAndFlush(review2)
        ).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("서로 다른 사용자는 동일한 콘텐츠에 리뷰를 등록할 수 있다")
    void save_reviews_ByDifferentAuthors() {
        // given
        Content content = createAndPersistContent(1);
        User author1 = createAuthor(1);
        User author2 = createAuthor(2);

        // when
        reviewRepository.saveAllAndFlush(List.of(
                createReview(content, author1, "재밌어요", 5.0),
                createReview(content, author2, "볼만해요", 4.0)
        ));

        // then
        assertThat(reviewRepository.count()).isEqualTo(2);
    }

    @Test
    @DisplayName("동일한 사용자는 서로 다른 콘텐츠에 리뷰를 등록할 수 있다")
    void save_reviews_for_differentContents() {
        // given
        Content content1 = createAndPersistContent(1);
        Content content2 = createAndPersistContent(2);
        User author = createAuthor(1);

        // when
        reviewRepository.saveAllAndFlush(List.of(
                createReview(content1, author, "재밌어요", 5.0),
                createReview(content2, author, "볼만해요", 4.0)
        ));

        // then
        assertThat(reviewRepository.count()).isEqualTo(2);
    }

    @Test
    @DisplayName("콘텐츠에 작성된 리뷰의 평균 평점을 조회한다.")
    void find_avgRating_ByContentId() {
        Content content = createAndPersistContent(1);

        User author1 = createAuthor(1);
        User author2 = createAuthor(2);
        User author3 = createAuthor(3);

        reviewRepository.saveAllAndFlush(List.of(
                createReview(content, author1, "재밌어요", 5.0),
                createReview(content, author2, "볼만해요", 4.0),
                createReview(content, author3, "그냥 그래요", 3.0)
        ));

        // when
        double averageRating =
                reviewRepository.findAverageRatingByContentId(content.getId());

        // then
        assertThat(averageRating).isEqualTo(4.0);
    }

    @Test
    @DisplayName("리뷰가 없는 콘텐츠의 평균 평점은 0.0을 반환한다.")
    void find_avgRating_ByContentId_noReviews() {
        // given
        Content content = createAndPersistContent(1);

        // when
        double averageRating =
                reviewRepository.findAverageRatingByContentId(content.getId());

        // then
        assertThat(averageRating).isZero();
    }

    @Test
    @DisplayName("콘텐츠에 작성된 리뷰 개수를 조회한다.")
    void count_ByContentId() {
        // given
        Content content1 = createAndPersistContent(1);
        Content content2 = createAndPersistContent(2);

        User author1 = createAuthor(1);
        User author2 = createAuthor(2);

        reviewRepository.saveAllAndFlush(List.of(
                createReview(content1, author1, "재밌어요", 5.0),
                createReview(content1, author2, "볼만해요", 4.0),
                createReview(content2, author2, "그냥 그래요", 3.0)
        ));

        // when
        long count1 = reviewRepository.countByContentId(content1.getId());
        long count2 = reviewRepository.countByContentId(content2.getId());

        // then
        assertThat(count1).isEqualTo(2L);
        assertThat(count2).isEqualTo(1L);
    }

    @Test
    @DisplayName("리뷰가 없는 콘텐츠의 리뷰 개수는 0을 반환한다.")
    void count_ByContentId_noReviews() {
        // given
        Content content = createAndPersistContent(1);

        // when
        long count = reviewRepository.countByContentId(content.getId());

        // then
        assertThat(count).isZero();
    }

    @Test
    @DisplayName("콘텐츠가 삭제되면 해당 콘텐츠의 모든 리뷰가 삭제된다.")
    void delete_ByContentId() {
        // given
        Content content = createAndPersistContent(1);
        Content otherContent = createAndPersistContent(2);

        User author1 = createAuthor(1);
        User author2 = createAuthor(2);
        User author3 = createAuthor(3);

        Review otherReview = createReview(otherContent, author2, "그냥 그래요", 3.0);

        reviewRepository.saveAllAndFlush(List.of(
                createReview(content, author1, "재밌어요", 5.0),
                createReview(content, author2, "볼만해요", 4.0),
                createReview(content, author3, "그냥 그래요", 3.0),
                otherReview
        ));

        // when
        int deleted = reviewRepository.deleteAllByContentId(content.getId());

        em.clear();

        // then
        assertThat(deleted).isEqualTo(3);
        assertThat(reviewRepository.count()).isEqualTo(1);
        assertThat(reviewRepository.existsById(otherReview.getId())).isTrue();
    }

}
