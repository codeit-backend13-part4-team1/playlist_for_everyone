package com.codeit.sb13.playlist_for_everyone.domain.review;

import com.codeit.sb13.playlist_for_everyone.domain.content.entity.Content;
import com.codeit.sb13.playlist_for_everyone.domain.content.entity.ContentSource;
import com.codeit.sb13.playlist_for_everyone.domain.content.entity.ContentType;
import com.codeit.sb13.playlist_for_everyone.domain.review.entity.Review;
import com.codeit.sb13.playlist_for_everyone.domain.review.repository.ReviewRepository;
import com.codeit.sb13.playlist_for_everyone.domain.user.entity.Role;
import com.codeit.sb13.playlist_for_everyone.domain.user.entity.User;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(properties = {
        "spring.flyway.enabled=true",
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.docker.compose.enabled=false"
})
@Testcontainers
@DisplayName("Review 통합 테스트")
public class ReviewIntegrationTest {

    @Container
    static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16");

    @DynamicPropertySource
    static void configurePostgres(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.datasource.driver-class-name", postgres::getDriverClassName);
    }

    @Autowired
    private ReviewRepository reviewRepository;

    @PersistenceContext
    private EntityManager em;

    private Content createAndPersistContent(String title) {
        Content content = BeanUtils.instantiateClass(Content.class);

        // TODO: createAdminContent() 구현 후 정적 팩토리 메서드 사용
        ReflectionTestUtils.setField(content, "type", ContentType.movie);
        ReflectionTestUtils.setField(content, "title", title);
        ReflectionTestUtils.setField(content, "description", title + " 입니다");
        ReflectionTestUtils.setField(content, "thumbnailUrl", "https://example.com/movie.png");
        ReflectionTestUtils.setField(content, "provider", ContentSource.ADMIN);

        em.persist(content);
        em.flush();

        return content;
    }

    private User createAuthor(String email, String name) {
        User author = User.builder()
                .email(email)
                .name(name)
                .password("encodedPassword")
                .role(Role.USER)
                .build();

        em.persist(author);
        em.flush();

        return author;
    }

    @Test
    @Transactional
    @DisplayName("Flyway 적용 후 동일 콘텐츠와 작성자의 리뷰 중복 등록을 방지한다")
    void duplicateReview() {
        // given
        Content content = createAndPersistContent("영화 1");
        User author = createAuthor("mopl1@gmail.com", "사용자 1");

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
    @Transactional
    @DisplayName("동일 콘텐츠에 서로 다른 작성자의 리뷰 등록을 허용한다")
    void allowDifferentAuthorsForSameContent() {
        Content content = createAndPersistContent("영화 1");

        User author1 = createAuthor("mopl1@gmail.com", "사용자 1");
        User author2 = createAuthor("mopl2@gmail.com", "사용자 2");

        Review review1 = Review.builder()
                .content(content)
                .author(author1)
                .text("재밌어요!")
                .rating(4.5)
                .build();

        Review review2 = Review.builder()
                .content(content)
                .author(author2)
                .text("좋아요!")
                .rating(5.0)
                .build();

        reviewRepository.saveAndFlush(review1);
        reviewRepository.saveAndFlush(review2);

        assertThat(reviewRepository.count()).isEqualTo(2);
    }

    @Test
    @Transactional
    @DisplayName("동일 작성자의 서로 다른 콘텐츠 리뷰 등록을 허용한다")
    void allowSameAuthorForDifferentContents() {
        Content content1 = createAndPersistContent("영화 1");
        Content content2 = createAndPersistContent("영화 2");

        User author = createAuthor("mopl1@gmail.com", "사용자 1");

        Review review1 = Review.builder()
                .content(content1)
                .author(author)
                .text("영화 1 리뷰")
                .rating(4.5)
                .build();

        Review review2 = Review.builder()
                .content(content2)
                .author(author)
                .text("영화 2 리뷰")
                .rating(5.0)
                .build();

        reviewRepository.saveAndFlush(review1);
        reviewRepository.saveAndFlush(review2);

        assertThat(reviewRepository.count()).isEqualTo(2);
    }

}
