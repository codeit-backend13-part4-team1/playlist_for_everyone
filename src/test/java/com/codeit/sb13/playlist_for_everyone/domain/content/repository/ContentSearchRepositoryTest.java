package com.codeit.sb13.playlist_for_everyone.domain.content.repository;

import com.codeit.sb13.playlist_for_everyone.domain.content.dto.condition.ContentSearchCondition;
import com.codeit.sb13.playlist_for_everyone.domain.content.dto.response.ContentSearchResult;
import com.codeit.sb13.playlist_for_everyone.domain.content.dto.response.ContentSummary;
import com.codeit.sb13.playlist_for_everyone.domain.content.entity.Content;
import com.codeit.sb13.playlist_for_everyone.domain.content.entity.ContentTag;
import com.codeit.sb13.playlist_for_everyone.domain.content.entity.ContentType;
import com.codeit.sb13.playlist_for_everyone.domain.content.mapper.ContentMapper;
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
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest(properties = {
        "spring.flyway.enabled=true",
        "spring.jpa.hibernate.ddl-auto=validate"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Import({
        JpaAuditingConfig.class,
        QueryDslConfig.class,
        ContentMapper.class
})
@Testcontainers
@DisplayName("ContentSearchRepository 테스트")
public class ContentSearchRepositoryTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16");

    @Autowired
    private ContentRepository contentRepository;

    @Autowired
    private TestEntityManager em;

    private Content createContent(ContentType contentType, String title, String description) {
         return Content.createAdminContent(
                contentType,
                title,
                description,
                null
        );
    }

    private User createAuthor(int number) {
        return User.builder()
                .email("test" + number + "@example.com")
                .name("테스트 사용자" + number)
                .password("encodedPassword")
                .role(Role.USER)
                .build();
    }

    private Review createReview(Content content, User author, String text, double rating) {
        return Review.builder()
                .content(content)
                .author(author)
                .text(text)
                .rating(rating)
                .build();
    }

    @BeforeEach
    void setUp() {
        Content movie1 = createContent(ContentType.movie, "영화시리즈1", "설명1");
        Content movie2 = createContent(ContentType.movie, "영화시리즈2", "설명2");
        Content sport1 = createContent(ContentType.sport, "스포츠1", "설명3");
        Content sport2 = createContent(ContentType.sport, "스포츠드라마2", "시리즈설명4");
        Content sport3 = createContent(ContentType.sport, "스포츠3", "설명5");
        Content tvSeries1 = createContent(ContentType.tvSeries, "드라마시리즈1", "설명6");

        List.of(movie1, movie2, sport1, sport2, sport3, tvSeries1)
                .forEach(em::persist);

        User user1 = createAuthor(1);
        User user2 = createAuthor(2);

        List.of(user1, user2).forEach(em::persist);

        List.of(
                createReview(movie1, user1, "재미있어요", 5),
                createReview(movie1, user2, "좋아요", 4),
                createReview(movie2, user1, "보통이에요", 3),
                createReview(movie2, user2, "괜찮아요", 3),
                createReview(sport1, user1, "최고예요", 5),
                createReview(sport2, user1, "아쉬워요", 2),
                createReview(tvSeries1, user1, "재미있어요", 4)
        ).forEach(em::persist);

        em.flush();
        em.clear();
    }

    @Test
    @DisplayName("콘텐츠 타입, 키워드, 태그 조건을 모두 만족하는 콘텐츠를 검색한다")
    void searchContents_byMultipleConditions() {
        // given
        Content movie = createContent(
                ContentType.movie,
                "액션시리즈",
                "액션 영화 설명"
        );
        movie.addTag("액션");
        movie.addTag("스릴러");

        em.persist(movie);
        em.flush();
        em.clear();

        ContentSearchCondition condition = new ContentSearchCondition(
                ContentType.movie,
                "시리즈",
                List.of("액션"),
                null,
                null,
                10,
                SortDirection.DESCENDING,
                "createdAt"
        );

        // when
        var result = contentRepository.search(condition);

        // then
        assertThat(result).hasSize(1);
        assertThat(result.get(0).content().getTitle()).isEqualTo("액션시리즈");
        assertThat(result.get(0).content().getType()).isEqualTo(ContentType.movie);
        assertThat(result.get(0).content().getTags())
                .extracting(ContentTag::getTag)
                .contains("액션");
    }

    @Test
    @DisplayName("생성일 기준으로 내림차순 정렬하여 검색한다")
    void searchContents_orderByCreatedAtDescending() {
        // given
        Content oldest = createContent(
                ContentType.movie, "정렬테스트 오래된 영화", "설명"
        );
        Content middle = createContent(
                ContentType.movie, "정렬테스트 중간 영화", "설명"
        );
        Content latest = createContent(
                ContentType.movie, "정렬테스트 최신 영화", "설명"
        );

        em.persist(oldest);
        em.persist(middle);
        em.persist(latest);
        em.flush();

        // 생성일을 명시적으로 설정
        em.getEntityManager().createNativeQuery(
                        "UPDATE contents SET created_at = :date WHERE id = :id"
                )
                .setParameter("date", LocalDateTime.of(2024, 1, 1, 0, 0))
                .setParameter("id", oldest.getId())
                .executeUpdate();

        em.getEntityManager().createNativeQuery(
                        "UPDATE contents SET created_at = :date WHERE id = :id"
                )
                .setParameter("date", LocalDateTime.of(2025, 1, 1, 0, 0))
                .setParameter("id", middle.getId())
                .executeUpdate();

        em.getEntityManager().createNativeQuery(
                        "UPDATE contents SET created_at = :date WHERE id = :id"
                )
                .setParameter("date", LocalDateTime.of(2026, 1, 1, 0, 0))
                .setParameter("id", latest.getId())
                .executeUpdate();

        em.clear();

        ContentSearchCondition condition = new ContentSearchCondition(
                ContentType.movie,
                "정렬테스트",
                null,
                null,
                null,
                10,
                SortDirection.DESCENDING,
                "createdAt"
        );

        // when
        var result = contentRepository.search(condition);

        // then
        assertThat(result)
                .extracting(searchResult -> searchResult.content().getId())
                .containsExactly(
                        latest.getId(),
                        middle.getId(),
                        oldest.getId()
                );
    }

    @Test
    @DisplayName("평점 기준으로 내림차순 정렬하여 검색한다.")
    void searchContents_orderByRatingDescending() {
        // given
        ContentSearchCondition condition = new ContentSearchCondition(
                null,
                null,
                null,
                null,
                null,
                10,
                SortDirection.DESCENDING,
                "rate"
        );

        // when
        var result = contentRepository.search(condition);

        // then
        assertThat(result).hasSize(6);
        assertThat(result)
                .extracting(searchResult -> searchResult.content().getTitle())
                .containsExactly(
                        "스포츠1",
                        "영화시리즈1",
                        "드라마시리즈1",
                        "영화시리즈2",
                        "스포츠드라마2",
                        "스포츠3"
                );
    }

    @Test
    @DisplayName("리뷰가 없는 콘텐츠는 평점과 리뷰 수가 0으로 조회된다")
    void searchContents_withoutReviews() {
        // given
        ContentSearchCondition condition = new ContentSearchCondition(
                ContentType.sport,
                "스포츠3",
                null,
                null,
                null,
                10,
                SortDirection.DESCENDING,
                "createdAt"
        );

        // when
        var result = contentRepository.search(condition);

        // then
        assertThat(result).hasSize(1);
        assertThat(result.get(0).averageRating()).isZero();
        assertThat(result.get(0).reviewCount()).isZero();
    }

    @Test
    @DisplayName("평점 커서 이후의 콘텐츠를 조회한다")
    void searchContents_afterRatingCursor() {
        // given
        ContentSearchCondition firstCondition = new ContentSearchCondition(
                null, null, null, null, null,
                2, SortDirection.DESCENDING, "rate"
        );

        var firstPage = contentRepository.search(firstCondition);

        // limit + 1개 조회하므로 실제 페이지의 마지막 콘텐츠는 index 1
        ContentSearchResult lastContent = firstPage.get(1);

        ContentSearchCondition nextCondition = new ContentSearchCondition(
                null,
                null,
                null,
                String.valueOf(lastContent.averageRating()),
                lastContent.content().getId(),
                2,
                SortDirection.DESCENDING,
                "rate"
        );

        // when
        var result = contentRepository.search(nextCondition);

        // then
        assertThat(result)
                .extracting(searchResult -> searchResult.content().getTitle())
                .containsExactly(
                        "드라마시리즈1",
                        "영화시리즈2",
                        "스포츠드라마2"
                );
    }

    @Test
    @DisplayName("콘텐츠 검색 시 필터 조건에 일치하는 전체 콘텐츠 수를 조회한다.")
    void countContents_bySearchCondition() {
        // given
        ContentSearchCondition condition = new ContentSearchCondition(
                ContentType.movie,
                "시리즈",
                null,
                null,
                null,
                2,
                SortDirection.DESCENDING,
                "rate"
        );

        // when
        long count = contentRepository.countBySearchCondition(condition);

        // then
        assertThat(count).isEqualTo(2L);
    }

}
