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
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.util.List;
import java.util.UUID;

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
}