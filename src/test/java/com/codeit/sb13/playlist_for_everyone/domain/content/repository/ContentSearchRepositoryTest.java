package com.codeit.sb13.playlist_for_everyone.domain.content.repository;

import com.codeit.sb13.playlist_for_everyone.domain.content.dto.condition.ContentSearchCondition;
import com.codeit.sb13.playlist_for_everyone.domain.content.dto.response.ContentResult;
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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
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
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

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

    private UUID movie1Id;
    private UUID movie2Id;
    private UUID sport3Id;

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

        movie1Id = movie1.getId();
        movie2Id = movie2.getId();
        sport3Id = sport3.getId();

        em.clear();
    }

    @Test
    @DisplayName("전달받은 콘텐츠 ID에 해당하는 콘텐츠만 조회한다.")
    void searchContents_byContentIds() {
        // given
        List<UUID> contentIds = List.of(movie1Id, movie2Id);

        ContentSearchCondition condition = new ContentSearchCondition(
                null, null, null, null, null,
                10, SortDirection.DESCENDING, "createdAt"
        );

        // when
        var result = contentRepository.searchByDatabase(
                condition, contentIds
        );

        // then
        assertThat(result)
                .extracting(searchResult -> searchResult.content().getId())
                .containsExactlyInAnyOrder(movie1Id, movie2Id);
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
                null, null, null, null, null,
                10, SortDirection.DESCENDING, "createdAt"
        );

        List<UUID> contentIds = List.of(
                oldest.getId(),
                middle.getId(),
                latest.getId()
        );

        // when
        var result = contentRepository.searchByDatabase(condition, contentIds);

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
        var result = contentRepository.searchByDatabase(condition, null);

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
    @DisplayName("리뷰가 있으면 평균 평점과 리뷰가 정확하게 조회된다.")
    void searchContents_withReviews() {
        // given
        ContentSearchCondition condition = new ContentSearchCondition(
                null, null, null, null, null,
                10,
                SortDirection.DESCENDING,
                "createdAt"
        );

        List<UUID> contentIds = List.of(movie1Id);

        // when
        var result = contentRepository.searchByDatabase(condition, contentIds);

        // then
        assertThat(result).hasSize(1);
        assertThat(result.get(0).averageRating()).isEqualTo(4.5);
        assertThat(result.get(0).reviewCount()).isEqualTo(2);
    }

    @Test
    @DisplayName("리뷰가 없는 콘텐츠는 평점과 리뷰 수가 0으로 조회된다")
    void searchContents_withoutReviews() {
        // given
        ContentSearchCondition condition = new ContentSearchCondition(
                null, null, null, null, null,
                10,
                SortDirection.DESCENDING,
                "createdAt"
        );

        List<UUID> contentIds = List.of(sport3Id);

        // when
        var result = contentRepository.searchByDatabase(condition, contentIds);

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

        var firstPage = contentRepository.searchByDatabase(firstCondition, null);

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
        var result = contentRepository.searchByDatabase(nextCondition, null);

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
    @DisplayName("전달받은 콘텐츠 ID에 해당하는 전체 콘텐츠 수를 조회한다.")
    void countContents_byContentIds() {
        // given
        List<UUID> contentIds = List.of(movie1Id, movie2Id);

        // when
        long count = contentRepository.countBySearchCondition(contentIds);

        // then
        assertThat(count).isEqualTo(2L);
    }

    @Test
    @DisplayName("시청자 수 정렬을 위한 검색 조건에 맞는 콘텐츠를 조회한다")
    void searchContents_ByWatcherCount() {
        // given
        List<UUID> contentIds = List.of(movie1Id, movie2Id);

        // when
        List<ContentSearchResult> results = contentRepository.searchForWatcherCount(contentIds);

        // then
        assertThat(results)
                .hasSize(2)
                .allSatisfy(result -> {
                    assertThat(result.content().getType())
                            .isEqualTo(ContentType.movie);
                    assertThat(result.content().getTitle())
                            .contains("시리즈");
                });
    }

    @ParameterizedTest
    @EnumSource(SortDirection.class)
    @DisplayName("동일한 평점에서 정렬 방향에 따라 UUID 순서로 페이지를 조회한다")
    void searchContents_sameRatingCursor(SortDirection direction) {
        // given
        List<Content> contents = List.of(
                createContent(ContentType.movie, "동일평점A", "설명"),
                createContent(ContentType.movie, "동일평점B", "설명"),
                createContent(ContentType.movie, "동일평점C", "설명"),
                createContent(ContentType.movie, "동일평점D", "설명")
        );

        contents.forEach(em::persist);
        em.flush();
        em.clear();

        // 리뷰가 없는 콘텐츠는 모두 평균 평점 0.0
        List<UUID> sortedIds = contents.stream()
                .map(Content::getId)
                .sorted(direction == SortDirection.DESCENDING
                        ? Comparator.comparing(UUID::toString).reversed()
                        : Comparator.comparing(UUID::toString))
                .toList();

        ContentSearchCondition firstCondition = new ContentSearchCondition(
                null, null, null, null, null,
                2,
                direction,
                "rate"
        );

        // when - 첫 페이지
        List<ContentSearchResult> firstResults = contentRepository.searchByDatabase(firstCondition, sortedIds);

        List<UUID> firstPageIds = firstResults.stream()
                .limit(2)
                .map(result -> result.content().getId())
                .toList();

        ContentSearchResult lastContent = firstResults.get(1);

        ContentSearchCondition nextCondition = new ContentSearchCondition(
                null, null, null,
                String.valueOf(lastContent.averageRating()),
                lastContent.content().getId(),
                2,
                direction,
                "rate"
        );

        // when - 다음 페이지
        List<ContentSearchResult> secondResults =
                contentRepository.searchByDatabase(nextCondition, sortedIds);

        List<UUID> secondPageIds = secondResults.stream()
                .limit(2)
                .map(result -> result.content().getId())
                .toList();

        // then
        assertThat(firstResults).hasSize(3); // limit + 1
        assertThat(firstPageIds).containsExactlyElementsOf(sortedIds.subList(0, 2));

        assertThat(lastContent.averageRating()).isZero();
        assertThat(lastContent.content().getId()).isEqualTo(sortedIds.get(1));

        assertThat(secondResults).hasSize(2);
        assertThat(secondPageIds).containsExactlyElementsOf(sortedIds.subList(2, 4));

        // 두 페이지를 합쳤을 때 중복·누락이 없는지 확인
        assertThat(Stream.concat(firstPageIds.stream(), secondPageIds.stream()).toList())
                .containsExactlyElementsOf(sortedIds);
    }

    @Test
    @DisplayName("콘텐츠 ID 조건이 없으면 전체 콘텐츠를 조회한다.")
    void searchContents_withoutContentIds() {
        // given
        ContentSearchCondition condition = new ContentSearchCondition(
                null, null, null, null, null,
                10,
                SortDirection.DESCENDING,
                "createdAt"
        );

        // when
        var result = contentRepository.searchByDatabase(condition, null);

        // then
        assertThat(result).hasSize(6);
    }

    @Test
    @DisplayName("대량의 콘텐츠 ID를 전달해도 전체 콘텐츠 수를 조회한다.")
    void countContents_withLargeContentIds() {
        // given
        List<UUID> contentIds = createLargeContentIds();

        // when
        long count = contentRepository.countBySearchCondition(contentIds);

        // then
        assertThat(count).isEqualTo(3L);
    }

    @Test
    @DisplayName("대량의 콘텐츠 ID를 전달해도 평점순으로 콘텐츠를 조회한다.")
    void searchContents_withLargeContentIds() {
        // given
        List<UUID> contentIds = createLargeContentIds();

        ContentSearchCondition condition = new ContentSearchCondition(
                null, null, null, null, null,
                1, SortDirection.DESCENDING, "rate"
        );

        // when
        var result = contentRepository.searchByDatabase(
                condition, contentIds
        );

        // then
        assertThat(result)
                .extracting(searchResult -> searchResult.content().getId())
                .containsExactly(movie1Id, movie2Id);
    }

    @Test
    @DisplayName("대량의 콘텐츠 ID를 전달해도 시청자 수 정렬 대상 콘텐츠를 조회한다.")
    void searchContents_byWatcherCountWithLargeContentIds() {
        // given
        List<UUID> contentIds = createLargeContentIds();

        // when
        var result = contentRepository.searchForWatcherCount(contentIds);

        // then
        assertThat(result)
                .extracting(searchResult -> searchResult.content().getId())
                .containsExactlyInAnyOrder(
                        movie1Id, movie2Id, sport3Id
                );
    }

    @Test
    @DisplayName("콘텐츠 ID 목록이 비어 있으면 검색 결과를 반환하지 않는다.")
    void searchContents_withEmptyContentIds() {
        // given
        List<UUID> contentIds = List.of();

        ContentSearchCondition condition = new ContentSearchCondition(
                null, null, null, null, null,
                10, SortDirection.DESCENDING, "createdAt"
        );

        // when & then
        assertThat(contentRepository.searchByDatabase(
                condition, contentIds
        )).isEmpty();

        assertThat(contentRepository.countBySearchCondition(
                contentIds
        )).isZero();

        assertThat(contentRepository.searchForWatcherCount(
                contentIds
        )).isEmpty();
    }

    /**
     * PostgreSQL 바인드 매개변수 제한을 검증하기 위해 70,000개의 후보 ID를 생성한다.
     * 실제 콘텐츠 ID를 목록 마지막에 배치하여 후보 ID 절삭으로 인한 조회 누락도 검증한다.
     */
    private List<UUID> createLargeContentIds() {
        return Stream.concat(
                Stream.generate(UUID::randomUUID).limit(69_997),
                Stream.of(movie1Id, movie2Id, sport3Id)
        ).toList();
    }

}
