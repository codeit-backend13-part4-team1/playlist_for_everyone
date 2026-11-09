package com.codeit.sb13.playlist_for_everyone.domain.content.collector.batch;

import com.codeit.sb13.playlist_for_everyone.domain.content.collector.ContentCollectionService;
import com.codeit.sb13.playlist_for_everyone.domain.content.collector.TmdbClient;
import com.codeit.sb13.playlist_for_everyone.domain.content.collector.dto.TmdbMovieResponse;
import com.codeit.sb13.playlist_for_everyone.domain.content.collector.dto.TmdbPageResponse;
import com.codeit.sb13.playlist_for_everyone.domain.content.collector.dto.TmdbTvResponse;
import com.codeit.sb13.playlist_for_everyone.domain.content.entity.ContentSource;
import com.codeit.sb13.playlist_for_everyone.domain.content.entity.ContentType;
import com.codeit.sb13.playlist_for_everyone.domain.content.repository.ContentRepository;
import com.codeit.sb13.playlist_for_everyone.global.config.JpaAuditingConfig;
import com.codeit.sb13.playlist_for_everyone.global.config.QueryDslConfig;
import org.junit.jupiter.api.Test;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.parameters.JobParametersBuilder;
import org.springframework.batch.core.launch.JobOperator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.batch.jdbc.autoconfigure.BatchJdbcAutoConfiguration;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.ResourceAccessException;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.batch.core.job.parameters.InvalidJobParametersException;

import java.util.List;
import java.util.UUID;
import java.util.ArrayList;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@DataJpaTest(properties = {
        "spring.flyway.enabled=true",
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.batch.job.enabled=false",
        "spring.batch.jdbc.initialize-schema=never"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@ImportAutoConfiguration(BatchJdbcAutoConfiguration.class)
@Import({
        JpaAuditingConfig.class,
        QueryDslConfig.class,
        ContentCollectionService.class,
        TmdbBatchConfig.class
})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@Testcontainers
class TmdbCollectionJobTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres =
            new PostgreSQLContainer("postgres:16");

    @Autowired
    private JobOperator jobOperator;

    @Autowired
    @Qualifier("tmdbCollectionJob")
    private Job job;

    @Autowired
    private ContentRepository contentRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @MockitoBean
    private TmdbClient tmdbClient;

    @Test
    void 배치를_실행하면_영화와_TV와_태그가_저장된다() throws Exception {
        var movie = new TmdbMovieResponse(
                550L,
                "테스트 영화",
                "영화 설명",
                "/poster.jpg",
                List.of(28, 18)
        );

        when(tmdbClient.getPopularMovies(1))
                .thenReturn(new TmdbPageResponse<>(
                        1, List.of(movie), 1, 1
                ));

        var tv = new TmdbTvResponse(
                550L,
                "테스트 드라마",
                "드라마 설명",
                "/tv-poster.jpg",
                List.of(18, 35)
        );

        when(tmdbClient.getPopularTvShows(1))
                .thenReturn(new TmdbPageResponse<>(
                        1, List.of(tv), 1, 1
                ));

        var parameters = new JobParametersBuilder()
                .addLong("maxPages", 1L)
                .addString("run.id", UUID.randomUUID().toString())
                .toJobParameters();

        var execution = jobOperator.start(job, parameters);

        assertThat(execution.getStatus())
                .isEqualTo(BatchStatus.COMPLETED);

        var saved = contentRepository.findByProviderAndTypeAndProviderItemId(
                ContentSource.TMDB,
                ContentType.movie,
                "550"
        );

        assertThat(saved).isPresent();

        var content = saved.orElseThrow();

        assertThat(content.getTitle()).isEqualTo("테스트 영화");
        assertThat(content.getDescription()).isEqualTo("영화 설명");
        assertThat(content.getThumbnailUrl())
                .isEqualTo("https://image.tmdb.org/t/p/w500/poster.jpg");

        var tags = jdbcTemplate.queryForList(
                "SELECT tag FROM content_tags WHERE content_id = ?",
                String.class,
                content.getId()
        );

        assertThat(tags).containsExactlyInAnyOrder("액션", "드라마");

        var savedTv = contentRepository.findByProviderAndTypeAndProviderItemId(
                ContentSource.TMDB,
                ContentType.tvSeries,
                "550"
        );

        assertThat(savedTv).isPresent();

        var tvContent = savedTv.orElseThrow();

        assertThat(tvContent.getId()).isNotEqualTo(content.getId());
        assertThat(tvContent.getTitle()).isEqualTo("테스트 드라마");
        assertThat(tvContent.getDescription()).isEqualTo("드라마 설명");
        assertThat(tvContent.getThumbnailUrl())
                .isEqualTo("https://image.tmdb.org/t/p/w500/tv-poster.jpg");

        var tvTags = jdbcTemplate.queryForList(
                "SELECT tag FROM content_tags WHERE content_id = ?",
                String.class,
                tvContent.getId()
        );

        assertThat(tvTags).containsExactlyInAnyOrder("드라마", "코미디");

        var executionCount = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM batch_job_execution
                WHERE job_execution_id = ?
                """,
                Long.class,
                execution.getId()
        );

        assertThat(executionCount).isEqualTo(1L);
    }

    @Test
    void 수집_실패_후_재시작하면_기존_저장을_유지하고_나머지를_저장한다()
            throws Exception {

        var first = new TmdbMovieResponse(
                990001L, "첫 번째 영화", "설명", "/first.jpg", List.of(28)
        );
        var second = new TmdbMovieResponse(
                990002L, "두 번째 영화", "설명", "/second.jpg", List.of(18)
        );

        when(tmdbClient.getPopularMovies(1))
                .thenReturn(new TmdbPageResponse<>(
                        1, List.of(first), 2, 2
                ));

        // 첫 요청은 실패하고, 재시작 후 요청은 성공한다.
        when(tmdbClient.getPopularMovies(2))
                .thenThrow(new ResourceAccessException("테스트용 연결 실패"))
                .thenReturn(new TmdbPageResponse<>(
                        2, List.of(second), 2, 2
                ));

        // 이번 테스트에서는 영화 수집의 실패·재시작에 집중한다.
        when(tmdbClient.getPopularTvShows(1))
                .thenReturn(new TmdbPageResponse<TmdbTvResponse>(
                        1, List.of(), 0, 0
                ));

        var parameters = new JobParametersBuilder()
                .addLong("maxPages", 2L)
                .addString("run.id", UUID.randomUUID().toString())
                .toJobParameters();

        var failedExecution = jobOperator.start(job, parameters);

        assertThat(failedExecution.getStatus())
                .isEqualTo(BatchStatus.FAILED);

        var firstSaved = contentRepository.findByProviderAndTypeAndProviderItemId(
                ContentSource.TMDB, ContentType.movie, "990001"
        );

        assertThat(firstSaved).isPresent();

        var firstId = firstSaved.orElseThrow().getId();

        assertThat(contentRepository.findByProviderAndTypeAndProviderItemId(
                ContentSource.TMDB, ContentType.movie, "990002"
        )).isEmpty();

        // 새 작업을 만들지 않고, 실패한 실행을 재시작한다.
        var restartedExecution = jobOperator.restart(failedExecution);

        assertThat(restartedExecution.getStatus())
                .isEqualTo(BatchStatus.COMPLETED);

        var firstAfterRestart =
                contentRepository.findByProviderAndTypeAndProviderItemId(
                        ContentSource.TMDB, ContentType.movie, "990001"
                );

        assertThat(firstAfterRestart).isPresent();
        assertThat(firstAfterRestart.orElseThrow().getId())
                .isEqualTo(firstId);

        var secondSaved =
                contentRepository.findByProviderAndTypeAndProviderItemId(
                        ContentSource.TMDB, ContentType.movie, "990002"
                );

        assertThat(secondSaved).isPresent();
        assertThat(secondSaved.orElseThrow().getTitle())
                .isEqualTo("두 번째 영화");

        var savedCount = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM contents
                WHERE provider = 'TMDB'
                  AND type = 'movie'
                  AND provider_item_id IN ('990001', '990002')
                """,
                Long.class
        );

        assertThat(savedCount).isEqualTo(2L);
    }

    @Test
    void 제목이_없는_영화는_건너뛰고_정상_영화는_저장한다()
            throws Exception {

        var first = new TmdbMovieResponse(
                990101L, "정상 영화 A", "설명", "/a.jpg", List.of(28)
        );
        var invalid = new TmdbMovieResponse(
                990102L, "   ", "설명", "/b.jpg", List.of(18)
        );
        var third = new TmdbMovieResponse(
                990103L, "정상 영화 C", "설명", "/c.jpg", List.of(35)
        );

        when(tmdbClient.getPopularMovies(1))
                .thenReturn(new TmdbPageResponse<>(
                        1, List.of(first, invalid, third), 1, 3
                ));

        when(tmdbClient.getPopularTvShows(1))
                .thenReturn(new TmdbPageResponse<TmdbTvResponse>(
                        1, List.of(), 0, 0
                ));

        var parameters = new JobParametersBuilder()
                .addLong("maxPages", 1L)
                .addString("run.id", UUID.randomUUID().toString())
                .toJobParameters();

        var execution = jobOperator.start(job, parameters);

        assertThat(execution.getStatus())
                .isEqualTo(BatchStatus.COMPLETED);

        assertThat(contentRepository.findByProviderAndTypeAndProviderItemId(
                ContentSource.TMDB, ContentType.movie, "990101"
        )).isPresent();

        assertThat(contentRepository.findByProviderAndTypeAndProviderItemId(
                ContentSource.TMDB, ContentType.movie, "990102"
        )).isEmpty();

        assertThat(contentRepository.findByProviderAndTypeAndProviderItemId(
                ContentSource.TMDB, ContentType.movie, "990103"
        )).isPresent();

        var movieStep = execution.getStepExecutions().stream()
                .filter(step -> step.getStepName()
                        .equals("tmdbMovieCollectionStep"))
                .findFirst()
                .orElseThrow();

        assertThat(movieStep.getWriteSkipCount()).isEqualTo(1L);
    }

    @Test
    void 잘못된_영화가_건너뛰기_제한을_초과하면_배치가_실패한다()
            throws Exception {

        var movies = new ArrayList<TmdbMovieResponse>();

        movies.add(new TmdbMovieResponse(
                990201L, "먼저 저장할 영화", "설명", "/first.jpg", List.of(28)
        ));

        // 제목이 없는 영화 11건
        for (int i = 0; i < 11; i++) {
            movies.add(new TmdbMovieResponse(
                    990210L + i, "", "설명", null, List.of()
            ));
        }

        movies.add(new TmdbMovieResponse(
                990299L, "중단 이후 영화", "설명", "/last.jpg", List.of(18)
        ));

        when(tmdbClient.getPopularMovies(1))
                .thenReturn(new TmdbPageResponse<>(
                        1, movies, 1, movies.size()
                ));

        var parameters = new JobParametersBuilder()
                .addLong("maxPages", 1L)
                .addString("run.id", UUID.randomUUID().toString())
                .toJobParameters();

        var execution = jobOperator.start(job, parameters);

        assertThat(execution.getStatus())
                .isEqualTo(BatchStatus.FAILED);

        // 실패 전에 커밋한 영화는 유지된다.
        assertThat(contentRepository.findByProviderAndTypeAndProviderItemId(
                ContentSource.TMDB, ContentType.movie, "990201"
        )).isPresent();

        // 제한 초과 이후의 영화는 처리하지 않는다.
        assertThat(contentRepository.findByProviderAndTypeAndProviderItemId(
                ContentSource.TMDB, ContentType.movie, "990299"
        )).isEmpty();

        var movieStep = execution.getStepExecutions().stream()
                .filter(step -> step.getStepName()
                        .equals("tmdbMovieCollectionStep"))
                .findFirst()
                .orElseThrow();

        assertThat(movieStep.getWriteSkipCount()).isEqualTo(10L);

        // 영화 Step이 실패했으므로 TV Step은 실행하지 않는다.
        verify(tmdbClient, never()).getPopularTvShows(1);
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(longs = {0L, -1L})
    void 수집_페이지_수가_없거나_잘못되면_배치_실행을_거부한다(
            Long maxPages
    ) {
        var builder = new JobParametersBuilder()
                .addString("run.id", UUID.randomUUID().toString());

        if (maxPages != null) {
            builder.addLong("maxPages", maxPages);
        }

        var parameters = builder.toJobParameters();

        assertThatThrownBy(() -> jobOperator.start(job, parameters))
                .isInstanceOf(InvalidJobParametersException.class)
                .hasMessage("maxPages는 1 이상의 정수여야 합니다.");

        verifyNoInteractions(tmdbClient);
    }

}