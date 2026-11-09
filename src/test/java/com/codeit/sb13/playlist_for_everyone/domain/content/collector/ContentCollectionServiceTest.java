package com.codeit.sb13.playlist_for_everyone.domain.content.collector;

import com.codeit.sb13.playlist_for_everyone.domain.content.collector.batch.TmdbMovieReader;
import com.codeit.sb13.playlist_for_everyone.domain.content.collector.dto.CollectedContentData;
import com.codeit.sb13.playlist_for_everyone.domain.content.collector.dto.SportsDbEventResponse;
import com.codeit.sb13.playlist_for_everyone.domain.content.collector.dto.TmdbMovieResponse;
import com.codeit.sb13.playlist_for_everyone.domain.content.collector.dto.TmdbPageResponse;
import com.codeit.sb13.playlist_for_everyone.domain.content.entity.Content;
import com.codeit.sb13.playlist_for_everyone.domain.content.entity.ContentSource;
import com.codeit.sb13.playlist_for_everyone.domain.content.entity.ContentTag;
import com.codeit.sb13.playlist_for_everyone.domain.content.entity.ContentType;
import com.codeit.sb13.playlist_for_everyone.domain.content.repository.ContentRepository;
import com.codeit.sb13.playlist_for_everyone.global.config.JpaAuditingConfig;
import com.codeit.sb13.playlist_for_everyone.global.config.QueryDslConfig;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;
import static org.mockito.Mockito.*;

@DataJpaTest(properties = {
        "spring.flyway.enabled=true",
        "spring.jpa.hibernate.ddl-auto=validate"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Import({JpaAuditingConfig.class, QueryDslConfig.class, ContentCollectionService.class})
@Testcontainers
class ContentCollectionServiceTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16");

    @Autowired
    private ContentCollectionService service;

    @Autowired
    private EntityManager em;

    @MockitoSpyBean
    private ContentRepository contentRepository;

    @Test
    void 수집한_영화와_태그를_함께_저장한다() {
        var data = new CollectedContentData(
                ContentType.movie, "테스트 영화", "영화 설명",
                "https://example.com/poster.jpg", ContentSource.TMDB, "550",
                List.of("액션", " 액션 ", "", "드라마")
        );

        Content saved = service.save(data);
        em.flush();
        em.clear();

        Content found = em.find(Content.class, saved.getId());

        assertThat(found).isNotNull();
        assertThat(found.getCreatedAt()).isNotNull();
        assertThat(found.getUpdatedAt()).isEqualTo(found.getCreatedAt());
        assertThat(found.getTitle()).isEqualTo("테스트 영화");
        assertThat(found.getProvider()).isEqualTo(ContentSource.TMDB);
        assertThat(found.getProviderItemId()).isEqualTo("550");
        assertThat(found.getTags())
                .extracting(ContentTag::getTag)
                .containsExactlyInAnyOrder("액션", "드라마");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    void 제목이_없으면_수집한_콘텐츠를_저장할_수_없다(String title) {
        var data = new CollectedContentData(
                ContentType.movie, title, "영화 설명",
                "https://example.com/poster.jpg", ContentSource.TMDB, "550",
                List.of("액션")
        );

        assertThatThrownBy(() -> service.save(data))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("수집한 콘텐츠에는 제목이 필요합니다.");
    }

    @Test
    void 썸네일이_없는_경기도_정보와_태그를_저장한다() {
        var data = new CollectedContentData(
                ContentType.sport, "West Ham vs Tottenham", "경기 설명",
                null, ContentSource.THE_SPORTS_DB, "441425",
                List.of("Soccer", "English Premier League")
        );

        Content saved = service.save(data);
        em.flush();
        em.clear();

        Content found = em.find(Content.class, saved.getId());

        assertThat(found).isNotNull();
        assertThat(found.getThumbnailUrl()).isNull();
        assertThat(found.getTitle()).isEqualTo("West Ham vs Tottenham");
        assertThat(found.getType()).isEqualTo(ContentType.sport);
        assertThat(found.getProvider()).isEqualTo(ContentSource.THE_SPORTS_DB);
        assertThat(found.getProviderItemId()).isEqualTo("441425");
        assertThat(found.getTags())
                .extracting(ContentTag::getTag)
                .containsExactlyInAnyOrder("Soccer", "English Premier League");
    }

    @Test
    void 스포츠_응답을_변환해서_콘텐츠와_태그를_저장한다() {
        var event = new SportsDbEventResponse(
                "441425",
                "West Ham vs Tottenham",
                "Soccer",
                "English Premier League",
                "2014-2015",
                "2014-08-16",
                null
        );

        var data = new SportsDbContentMapper().fromEvent(event);

        Content saved = service.save(data);
        em.flush();
        em.clear();

        Content found = em.find(Content.class, saved.getId());

        assertThat(found).isNotNull();
        assertThat(found.getType()).isEqualTo(ContentType.sport);
        assertThat(found.getTitle()).isEqualTo("West Ham vs Tottenham");
        assertThat(found.getDescription())
                .isEqualTo("English Premier League / 2014-2015 / 2014-08-16");
        assertThat(found.getThumbnailUrl()).isNull();
        assertThat(found.getProvider()).isEqualTo(ContentSource.THE_SPORTS_DB);
        assertThat(found.getProviderItemId()).isEqualTo("441425");
        assertThat(found.getTags())
                .extracting(ContentTag::getTag)
                .containsExactlyInAnyOrder("Soccer", "English Premier League");
    }

    @ParameterizedTest
    @ValueSource(strings = {"가", "😀"})
    void 제목이_100자를_넘으면_저장할_수_없다(String character) {
        var data = new CollectedContentData(
                ContentType.movie, character.repeat(101), "영화 설명",
                "https://example.com/poster.jpg", ContentSource.TMDB, "550",
                List.of("액션")
        );

        assertThatThrownBy(() -> service.save(data))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("수집한 콘텐츠 제목은 100자 이하여야 합니다.");
    }

    @ParameterizedTest
    @ValueSource(strings = {"가", "😀"})
    void 태그가_50자를_넘으면_저장할_수_없다(String character) {
        var data = new CollectedContentData(
                ContentType.movie, "테스트 영화", "영화 설명",
                "https://example.com/poster.jpg", ContentSource.TMDB, "550",
                List.of(character.repeat(51))
        );

        assertThatThrownBy(() -> service.save(data))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("수집한 콘텐츠 태그는 50자 이하여야 합니다.");
    }

    @ParameterizedTest
    @ValueSource(strings = {"가", "😀"})
    void 제목_100자와_공백을_제거한_태그_50자는_저장할_수_있다(
            String character
    ) {
        String title = character.repeat(100);
        String tag = character.repeat(50);

        var data = new CollectedContentData(
                ContentType.movie, title, "영화 설명",
                "https://example.com/poster.jpg", ContentSource.TMDB, "550",
                List.of(" " + tag + " ")
        );

        Content saved = service.save(data);
        em.flush();
        em.clear();

        Content found = em.find(Content.class, saved.getId());

        assertThat(found.getTitle()).isEqualTo(title);
        assertThat(found.getTags())
                .extracting(ContentTag::getTag)
                .containsExactly(tag);
    }

    @Test
    void 같은_외부_콘텐츠를_다시_수집하면_기존_콘텐츠를_반환한다() {
        var data = new CollectedContentData(
                ContentType.movie, "테스트 영화", "영화 설명",
                "https://example.com/poster.jpg", ContentSource.TMDB, "550",
                List.of("액션")
        );

        Content first = service.save(data);
        em.flush();
        em.clear();

        Content second = service.save(data);
        em.flush();
        em.clear();

        assertThat(second.getId()).isEqualTo(first.getId());

        Long count = em.createQuery(
                        """
                        select count(c) from Content c
                        where c.provider = :provider
                          and c.type = :type
                          and c.providerItemId = :externalId
                        """,
                        Long.class
                )
                .setParameter("provider", ContentSource.TMDB)
                .setParameter("type", ContentType.movie)
                .setParameter("externalId", "550")
                .getSingleResult();

        assertThat(count).isEqualTo(1L);
    }

    @Test
    void 외부_ID가_같아도_타입이_다르면_각각_저장한다() {
        var movie = new CollectedContentData(
                ContentType.movie, "테스트 영화", "영화 설명",
                null, ContentSource.TMDB, "550",
                List.of("액션")
        );

        var tv = new CollectedContentData(
                ContentType.tvSeries, "테스트 드라마", "드라마 설명",
                null, ContentSource.TMDB, "550",
                List.of("드라마")
        );

        Content savedMovie = service.save(movie);
        em.flush();
        em.clear();

        Content savedTv = service.save(tv);
        em.flush();
        em.clear();

        assertThat(savedMovie.getId()).isNotEqualTo(savedTv.getId());

        Content foundMovie = em.find(Content.class, savedMovie.getId());
        Content foundTv = em.find(Content.class, savedTv.getId());

        assertThat(foundMovie.getType()).isEqualTo(ContentType.movie);
        assertThat(foundTv.getType()).isEqualTo(ContentType.tvSeries);
    }

    @Test
    void 같은_콘텐츠를_재수집해도_기존_정보와_태그를_유지한다() {
        var original = new CollectedContentData(
                ContentType.movie, "기존 제목", "기존 설명",
                "https://example.com/original.jpg", ContentSource.TMDB, "550",
                List.of("액션")
        );

        Content first = service.save(original);
        em.flush();
        em.clear();

        var recollected = new CollectedContentData(
                ContentType.movie, "새 제목", "새 설명",
                "https://example.com/new.jpg", ContentSource.TMDB, "550",
                List.of("드라마", "코미디")
        );

        Content second = service.save(recollected);
        em.flush();
        em.clear();

        assertThat(second.getId()).isEqualTo(first.getId());

        Content found = em.find(Content.class, first.getId());

        assertThat(found.getTitle()).isEqualTo("기존 제목");
        assertThat(found.getDescription()).isEqualTo("기존 설명");
        assertThat(found.getThumbnailUrl())
                .isEqualTo("https://example.com/original.jpg");
        assertThat(found.getTags())
                .extracting(ContentTag::getTag)
                .containsExactly("액션");
    }

    @Test
    void 외부_ID와_타입이_같아도_출처가_다르면_각각_저장한다() {
        var tmdbData = new CollectedContentData(
                ContentType.movie, "첫 번째 콘텐츠", "설명",
                null, ContentSource.TMDB, "550",
                List.of()
        );

        var sportsDbData = new CollectedContentData(
                ContentType.movie, "두 번째 콘텐츠", "설명",
                null, ContentSource.THE_SPORTS_DB, "550",
                List.of()
        );

        Content first = service.save(tmdbData);
        em.flush();
        em.clear();

        Content second = service.save(sportsDbData);
        em.flush();
        em.clear();

        assertThat(first.getId()).isNotEqualTo(second.getId());

        Content foundFirst = em.find(Content.class, first.getId());
        Content foundSecond = em.find(Content.class, second.getId());

        assertThat(foundFirst.getProvider()).isEqualTo(ContentSource.TMDB);
        assertThat(foundSecond.getProvider())
                .isEqualTo(ContentSource.THE_SPORTS_DB);
    }

    @Test
    void DB는_동일한_출처와_타입과_외부_ID의_중복을_허용하지_않는다() {
        Content first = Content.createCollectedContent(
                ContentType.movie, "첫 번째 제목", "설명",
                null, ContentSource.TMDB, "550"
        );

        em.persist(first);
        em.flush();

        Content duplicate = Content.createCollectedContent(
                ContentType.movie, "두 번째 제목", "설명",
                null, ContentSource.TMDB, "550"
        );

        em.persist(duplicate);

        assertThatThrownBy(() -> em.flush())
                .isInstanceOf(
                        org.hibernate.exception.ConstraintViolationException.class
                )
                .satisfies(exception -> {
                    var violation =
                            (org.hibernate.exception.ConstraintViolationException) exception;

                    assertThat(violation.getConstraintName())
                            .isEqualTo("uk_content_provider_type_item_id");
                });
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void 같은_콘텐츠를_동시에_수집해도_하나만_저장한다() throws Exception {
        String externalId = UUID.randomUUID().toString();

        var data = new CollectedContentData(
                ContentType.movie, "동시 수집 영화", "영화 설명",
                null, ContentSource.TMDB, externalId,
                List.of("액션")
        );

        var barrier = new CyclicBarrier(2);
        var lookupCount = new AtomicInteger();

        // 두 요청이 모두 조회를 끝낸 뒤 저장하도록 맞춘다.
        doAnswer(invocation -> {
            var result = em.createQuery(
                            """
                            select c from Content c
                            where c.provider = :provider
                              and c.type = :type
                              and c.providerItemId = :externalId
                            """,
                            Content.class
                    )
                    .setParameter("provider", ContentSource.TMDB)
                    .setParameter("type", ContentType.movie)
                    .setParameter("externalId", externalId)
                    .getResultList()
                    .stream()
                    .findFirst();

            if (lookupCount.incrementAndGet() <= 2) {
                barrier.await(5, TimeUnit.SECONDS);
            }

            return result;
        }).when(contentRepository).findByProviderAndTypeAndProviderItemId(
                ContentSource.TMDB,
                ContentType.movie,
                externalId
        );

        var executor = Executors.newFixedThreadPool(2);

        try {
            Callable<Content> saveTask = () -> service.save(data);

            var results = executor.invokeAll(
                    List.of(saveTask, saveTask),
                    20,
                    TimeUnit.SECONDS
            );

            Content first = results.get(0).get();
            Content second = results.get(1).get();

            assertThat(first.getId()).isEqualTo(second.getId());

            Long count = em.createQuery(
                            """
                            select count(c) from Content c
                            where c.provider = :provider
                              and c.type = :type
                              and c.providerItemId = :externalId
                            """,
                            Long.class
                    )
                    .setParameter("provider", ContentSource.TMDB)
                    .setParameter("type", ContentType.movie)
                    .setParameter("externalId", externalId)
                    .getSingleResult();

            assertThat(count).isEqualTo(1L);

            List<String> savedTags = em.createQuery(
                            """
                            select t.tag from ContentTag t
                            where t.content.id = :contentId
                            """,
                            String.class
                    )
                    .setParameter("contentId", first.getId())
                    .getResultList();

            assertThat(savedTags).containsExactly("액션");

        } finally {
            executor.shutdownNow();

            boolean terminated = executor.awaitTermination(
                    10, TimeUnit.SECONDS
            );
            assertThat(terminated).isTrue();

            // 별도 요청에서 저장한 데이터는 직접 정리한다.
            contentRepository.findByProviderAndTypeAndProviderItemId(
                    ContentSource.TMDB,
                    ContentType.movie,
                    externalId
            ).ifPresent(content ->
                    contentRepository.deleteById(content.getId())
            );
        }
    }

}