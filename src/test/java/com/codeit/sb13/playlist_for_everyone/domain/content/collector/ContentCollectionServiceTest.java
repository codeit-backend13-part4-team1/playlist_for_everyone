package com.codeit.sb13.playlist_for_everyone.domain.content.collector;

import com.codeit.sb13.playlist_for_everyone.domain.content.collector.exception.CollectedContentValidationException;
import com.codeit.sb13.playlist_for_everyone.domain.content.collector.dto.CollectedContentData;
import com.codeit.sb13.playlist_for_everyone.domain.content.collector.dto.SportsDbEventResponse;
import com.codeit.sb13.playlist_for_everyone.domain.content.entity.Content;
import com.codeit.sb13.playlist_for_everyone.domain.content.entity.ContentSource;
import com.codeit.sb13.playlist_for_everyone.domain.content.entity.ContentTag;
import com.codeit.sb13.playlist_for_everyone.domain.content.entity.ContentType;
import com.codeit.sb13.playlist_for_everyone.domain.content.event.cache.ContentSearchCacheEvictEvent;
import com.codeit.sb13.playlist_for_everyone.domain.content.repository.ContentRepository;
import com.codeit.sb13.playlist_for_everyone.global.config.JpaAuditingConfig;
import com.codeit.sb13.playlist_for_everyone.global.config.QueryDslConfig;
import com.codeit.sb13.playlist_for_everyone.global.file.entity.FileInfo;
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
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClient;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

@DataJpaTest(properties = {
        "spring.flyway.enabled=true",
        "spring.jpa.hibernate.ddl-auto=validate"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Import({JpaAuditingConfig.class, QueryDslConfig.class, ContentCollectionService.class})
@Testcontainers
@RecordApplicationEvents
class ContentCollectionServiceTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16");

    @Autowired
    private ContentCollectionService service;

    @Autowired
    private EntityManager em;

    @Autowired
    private ApplicationEvents applicationEvents;

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
                .isInstanceOf(CollectedContentValidationException.class)
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
                .isInstanceOf(CollectedContentValidationException.class)
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
                .isInstanceOf(CollectedContentValidationException.class)
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
    void 같은_콘텐츠를_재수집하면_변경된_정보와_태그를_반영한다() {
        var original = new CollectedContentData(
                ContentType.movie, "기존 제목", "기존 설명",
                "https://example.com/original.jpg", ContentSource.TMDB, "550",
                List.of("액션"));

        Content first = service.save(original);
        em.flush();
        em.clear();

        var recollected = new CollectedContentData(
                ContentType.movie, "새 제목", "새 설명",
                "https://example.com/new.jpg", ContentSource.TMDB, "550",
                List.of("드라마", "코미디"));

        Content second = service.save(recollected);
        em.flush();
        em.clear();

        assertThat(second.getId()).isEqualTo(first.getId());

        Content found = em.find(Content.class, first.getId());

        assertThat(found.getTitle()).isEqualTo("새 제목");
        assertThat(found.getDescription()).isEqualTo("새 설명");
        assertThat(found.getThumbnailUrl())
                .isEqualTo("https://example.com/new.jpg");
        assertThat(found.getTags())
                .extracting(ContentTag::getTag)
                .containsExactlyInAnyOrder("드라마", "코미디");
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

    @Test
    void 새_콘텐츠를_저장하면_캐시_무효화_이벤트를_발행한다() {
        var data = new CollectedContentData(
                ContentType.movie, "테스트 영화", "영화 설명",
                "https://example.com/poster.jpg",
                ContentSource.TMDB, "550",
                List.of("액션")
        );

        service.save(data);
        em.flush();

        assertThat(applicationEvents
                .stream(ContentSearchCacheEvictEvent.class)
                .count())
                .isEqualTo(1L);
    }

    @Test
    void 수집_콘텐츠가_갱신되면_캐시_무효화_이벤트를_발행한다() {
        var original = new CollectedContentData(
                ContentType.movie, "기존 제목", "기존 설명",
                "https://example.com/original.jpg", ContentSource.TMDB, "550",
                List.of("액션")
        );

        Content first = service.save(original);
        em.flush();
        em.clear();
        applicationEvents.clear();

        var recollected = new CollectedContentData(
                ContentType.movie, "새 제목", "새 설명",
                "https://example.com/new.jpg", ContentSource.TMDB, "550",
                List.of("드라마")
        );

        Content updated = service.save(recollected);
        em.flush();

        assertThat(updated.getId()).isEqualTo(first.getId());
        assertThat(applicationEvents
                .stream(ContentSearchCacheEvictEvent.class)
                .count())
                .isEqualTo(1L);
    }

    @Test
    void 중복_콘텐츠를_수집하면_캐시_무효화_이벤트를_발행하지_않는다() {
        var data = new CollectedContentData(
                ContentType.movie, "테스트 영화", "영화 설명",
                "https://example.com/poster.jpg",
                ContentSource.TMDB, "550",
                List.of("액션")
        );

        service.save(data);
        em.flush();

        // 처음 저장할 때 기록된 이벤트는 비운다.
        applicationEvents.clear();

        service.save(data);
        em.flush();

        assertThat(applicationEvents
                .stream(ContentSearchCacheEvictEvent.class)
                .count())
                .isZero();
    }

    @Test
    void INSERT가_중복으로_건너뛰어지면_캐시_이벤트를_발행하지_않는다() {
        var data = new CollectedContentData(
                ContentType.movie, "테스트 영화", "영화 설명",
                "https://example.com/poster.jpg",
                ContentSource.TMDB, "550",
                List.of("액션")
        );

        Content existing = service.save(data);
        em.flush();
        applicationEvents.clear();

        // 처음에는 없는 것으로 조회하고, 저장 후에는 기존 콘텐츠를 반환한다.
        doReturn(Optional.empty(), Optional.of(existing))
                .when(contentRepository)
                .findByProviderAndTypeAndProviderItemId(
                        ContentSource.TMDB,
                        ContentType.movie,
                        "550"
                );

        Content result = service.save(data);
        em.flush();

        assertThat(result.getId()).isEqualTo(existing.getId());
        assertThat(applicationEvents
                .stream(ContentSearchCacheEvictEvent.class)
                .count())
                .isZero();
    }

    @Test
    void 배치용_저장은_신규_저장_여부를_반환하고_이벤트는_발행하지_않는다() {
        var data = new CollectedContentData(
                ContentType.movie, "테스트 영화", "영화 설명",
                "https://example.com/poster.jpg",
                ContentSource.TMDB, "550",
                List.of("액션")
        );

        var result = service.saveForBatch(data);
        em.flush();

        assertThat(result.inserted()).isTrue();
        assertThat(result.content().getId()).isNotNull();
        assertThat(applicationEvents
                .stream(ContentSearchCacheEvictEvent.class)
                .count())
                .isZero();
    }

    @Test
    void 배치용_저장은_중복이면_기존_콘텐츠와_false를_반환한다() {
        var data = new CollectedContentData(
                ContentType.movie, "테스트 영화", "영화 설명",
                "https://example.com/poster.jpg",
                ContentSource.TMDB, "550",
                List.of("액션")
        );

        Content existing = service.save(data);
        em.flush();
        applicationEvents.clear();

        var result = service.saveForBatch(data);
        em.flush();

        assertThat(result.inserted()).isFalse();
        assertThat(result.content().getId()).isEqualTo(existing.getId());
        assertThat(applicationEvents
                .stream(ContentSearchCacheEvictEvent.class)
                .count())
                .isZero();
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void API_재시도_후_같은_경기를_저장해도_중복되지_않는다() {
        String externalId = UUID.randomUUID().toString();
        String url = "https://sportsdb.test/123/eventsseason.php"
                + "?id=4328&s=2014-2015";

        var builder = RestClient.builder()
                .baseUrl("https://sportsdb.test/123");
        var server = MockRestServiceServer.bindTo(builder).build();
        var client = new SportsDbClient(builder.build(), new ExternalApiRetry(Duration.ZERO));
        var mapper = new SportsDbContentMapper();

        String responseBody = """
            {
              "events": [{
                "idEvent": "%s",
                "strEvent": "West Ham vs Tottenham",
                "strSport": "Soccer",
                "strLeague": "English Premier League",
                "strSeason": "2014-2015",
                "dateEvent": "2014-08-16"
              }]
            }
            """.formatted(externalId);

        // 첫 수집과 재수집 모두 503 이후 정상 응답
        for (int i = 0; i < 2; i++) {
            server.expect(requestTo(url))
                    .andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));
            server.expect(requestTo(url))
                    .andRespond(withSuccess(responseBody, MediaType.APPLICATION_JSON));
        }

        var firstResponse = client.getEventsBySeason("4328", "2014-2015");
        var first = service.saveForBatch(mapper.fromEvent(firstResponse.events().get(0)));

        var secondResponse = client.getEventsBySeason("4328", "2014-2015");
        var second = service.saveForBatch(mapper.fromEvent(secondResponse.events().get(0)));

        assertThat(first.inserted()).isTrue();
        assertThat(second.inserted()).isFalse();
        assertThat(second.content().getId()).isEqualTo(first.content().getId());

        Long count = em.createQuery("""
                    select count(c) from Content c
                    where c.provider = :provider
                      and c.type = :type
                      and c.providerItemId = :externalId
                    """, Long.class)
                .setParameter("provider", ContentSource.THE_SPORTS_DB)
                .setParameter("type", ContentType.sport)
                .setParameter("externalId", externalId)
                .getSingleResult();

        assertThat(count).isEqualTo(1L);

        var tags = em.createQuery("""
                    select t.tag from ContentTag t
                    where t.content.id = :contentId
                    """, String.class)
                .setParameter("contentId", first.content().getId())
                .getResultList();

        assertThat(tags).containsExactlyInAnyOrder("Soccer", "English Premier League");

        server.verify();
    }

    //
    @Test
    void 재수집해도_관리자_업로드_이미지는_유지한다() {
        var original = new CollectedContentData(
                ContentType.movie, "기존 제목", "영화 설명",
                "https://example.com/old.jpg", ContentSource.TMDB, "550",
                List.of("액션")
        );

        Content saved = service.save(original);

        var fileInfo = FileInfo.createS3File(
                "test/admin-thumbnail.jpg",
                "admin-thumbnail.jpg",
                "image/jpeg",
                100L
        );
        em.persist(fileInfo);

        saved.updateThumbnail(null, fileInfo);

        var contentId = saved.getId();
        var fileId = fileInfo.getId();

        em.flush();
        em.clear();

        var recollected = new CollectedContentData(
                ContentType.movie, "변경된 제목", "영화 설명",
                "https://example.com/new.jpg", ContentSource.TMDB, "550",
                List.of("액션")
        );

        service.save(recollected);

        em.flush();
        em.clear();

        Content updated = em.find(Content.class, contentId);

        assertThat(updated.getTitle()).isEqualTo("변경된 제목");
        assertThat(updated.getThumbnailUrl())
                .isEqualTo("https://example.com/new.jpg");
        assertThat(updated.getFileInfo()).isNotNull();
        assertThat(updated.getFileInfo().getId()).isEqualTo(fileId);
        assertThat(em.find(FileInfo.class, fileId)).isNotNull();
    }

}