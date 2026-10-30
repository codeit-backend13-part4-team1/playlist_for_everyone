package com.codeit.sb13.playlist_for_everyone.domain.review.repository;

import com.codeit.sb13.playlist_for_everyone.domain.content.entity.Content;
import com.codeit.sb13.playlist_for_everyone.domain.content.entity.ContentSource;
import com.codeit.sb13.playlist_for_everyone.domain.content.entity.ContentType;
import com.codeit.sb13.playlist_for_everyone.domain.review.entity.Review;
import com.codeit.sb13.playlist_for_everyone.domain.user.entity.Role;
import com.codeit.sb13.playlist_for_everyone.domain.user.entity.User;
import com.codeit.sb13.playlist_for_everyone.global.config.JpaAuditingConfig;
import com.codeit.sb13.playlist_for_everyone.global.config.QueryDslConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@Import({
        QueryDslConfig.class
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

    @Test
    @DisplayName("동일한 콘텐츠에 동일한 사용자가 리뷰를 중복 등록할 수 없다")
    void duplicateReview() {
        // given
        Content content = createAndPersistContent(1);
        User author = createAuthor(1);

        Review review1 = Review.builder()
                .content(content)
                .author(author)
                .text("재밌어요!")
                .rating(4.5)
                .build();

        Review review2 = Review.builder()
                .content(content)
                .author(author)
                .text("정말 좋아요!")
                .rating(5.0)
                .build();

        // when
        reviewRepository.saveAndFlush(review1);

        // then
        assertThatThrownBy(() ->
                reviewRepository.saveAndFlush(review2)
        ).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("서로 다른 사용자는 동일한 콘텐츠에 리뷰를 등록할 수 있다")
    void saveReviewsByDifferentAuthors() {
        // given
        Content content = createAndPersistContent(1);
        User author1 = createAuthor(1);

        User author2 = User.builder()
                .email("test2@example.com")
                .name("테스트 사용자 2")
                .password("encodedPassword")
                .role(Role.USER)
                .build();

        em.persistAndFlush(author2);

        Review review1 = Review.builder()
                .content(content)
                .author(author1)
                .text("재밌어요!")
                .rating(4.5)
                .build();

        Review review2 = Review.builder()
                .content(content)
                .author(author2)
                .text("정말 좋아요!")
                .rating(5.0)
                .build();

        // when
        reviewRepository.saveAndFlush(review1);
        reviewRepository.saveAndFlush(review2);

        // then
        assertThat(reviewRepository.count()).isEqualTo(2);
    }

    @Test
    @DisplayName("동일한 사용자는 서로 다른 콘텐츠에 리뷰를 등록할 수 있다")
    void saveReviewsForDifferentContents() {
        // given
        Content content1 = createAndPersistContent(1);
        Content content2 = createAndPersistContent(2);
        User author = createAuthor(1);

        Review review1 = Review.builder()
                .content(content1)
                .author(author)
                .text("첫 번째 영화 재밌어요!")
                .rating(4.5)
                .build();

        Review review2 = Review.builder()
                .content(content2)
                .author(author)
                .text("두 번째 영화도 좋아요!")
                .rating(5.0)
                .build();

        // when
        reviewRepository.saveAndFlush(review1);
        reviewRepository.saveAndFlush(review2);

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

        Review review1 = Review.builder()
                .content(content)
                .author(author1)
                .text("그냥 그런듯")
                .rating(3.0)
                .build();

        Review review2 = Review.builder()
                .content(content)
                .author(author2)
                .text("낫 밷 ~")
                .rating(4.0)
                .build();

        Review review3 = Review.builder()
                .content(content)
                .author(author3)
                .text("존 잼 입 니 다 강 추")
                .rating(5.0)
                .build();

        reviewRepository.saveAllAndFlush(List.of(review1, review2, review3));

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

        Review review1 = Review.builder()
                .content(content1)
                .author(author1)
                .text("재밌어요")
                .rating(5.0)
                .build();

        Review review2 = Review.builder()
                .content(content1)
                .author(author2)
                .text("볼만해요")
                .rating(4.0)
                .build();

        Review review3 = Review.builder()
                .content(content2)
                .author(author2)
                .text("그냥 그래요")
                .rating(3.0)
                .build();

        reviewRepository.saveAllAndFlush(List.of(review1, review2, review3));

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

}
