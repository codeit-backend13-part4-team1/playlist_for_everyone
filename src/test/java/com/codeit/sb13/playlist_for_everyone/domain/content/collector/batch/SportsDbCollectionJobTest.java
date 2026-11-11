package com.codeit.sb13.playlist_for_everyone.domain.content.collector.batch;

import com.codeit.sb13.playlist_for_everyone.domain.content.collector.ContentCollectionService;
import com.codeit.sb13.playlist_for_everyone.domain.content.collector.SportsDbClient;
import com.codeit.sb13.playlist_for_everyone.domain.content.collector.dto.SportsDbEventResponse;
import com.codeit.sb13.playlist_for_everyone.domain.content.collector.dto.SportsDbEventsResponse;
import com.codeit.sb13.playlist_for_everyone.domain.content.entity.ContentSource;
import com.codeit.sb13.playlist_for_everyone.domain.content.entity.ContentType;
import com.codeit.sb13.playlist_for_everyone.domain.content.repository.ContentRepository;
import com.codeit.sb13.playlist_for_everyone.global.config.JpaAuditingConfig;
import com.codeit.sb13.playlist_for_everyone.global.config.QueryDslConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.parameters.InvalidJobParametersException;
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
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;
import static org.mockito.Mockito.verifyNoInteractions;
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
        SportsDbBatchConfig.class
})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@Testcontainers
class SportsDbCollectionJobTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres =
            new PostgreSQLContainer("postgres:16");

    @Autowired
    private JobOperator jobOperator;

    @Autowired
    @Qualifier("sportsDbCollectionJob")
    private Job job;

    @Autowired
    private ContentRepository contentRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @MockitoBean
    private SportsDbClient sportsDbClient;

    @Test
    void 배치를_실행하면_경기와_태그가_저장된다() throws Exception {
        var event = new SportsDbEventResponse(
                "441425",
                "West Ham vs Tottenham",
                "Soccer",
                "English Premier League",
                "2014-2015",
                "2014-08-16",
                "https://example.com/event.jpg"
        );

        when(sportsDbClient.getEventsBySeason("4328", "2014-2015"))
                .thenReturn(new SportsDbEventsResponse(List.of(event)));

        var parameters = new JobParametersBuilder()
                .addString("leagueId", "4328")
                .addString("season", "2014-2015")
                .addString("run.id", UUID.randomUUID().toString())
                .toJobParameters();

        var execution = jobOperator.start(job, parameters);

        assertThat(execution.getStatus())
                .isEqualTo(BatchStatus.COMPLETED);

        var saved = contentRepository.findByProviderAndTypeAndProviderItemId(
                ContentSource.THE_SPORTS_DB,
                ContentType.sport,
                "441425"
        );

        assertThat(saved).isPresent();

        var content = saved.orElseThrow();

        assertThat(content.getTitle())
                .isEqualTo("West Ham vs Tottenham");
        assertThat(content.getDescription())
                .isEqualTo("English Premier League / 2014-2015 / 2014-08-16");
        assertThat(content.getThumbnailUrl())
                .isEqualTo("https://example.com/event.jpg");

        var tags = jdbcTemplate.queryForList(
                "SELECT tag FROM content_tags WHERE content_id = ?",
                String.class,
                content.getId()
        );

        assertThat(tags)
                .containsExactlyInAnyOrder("Soccer", "English Premier League");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    void 리그_ID가_없으면_배치_실행을_거부한다(String leagueId) {
        var builder = new JobParametersBuilder()
                .addString("season", "2014-2015")
                .addString("run.id", UUID.randomUUID().toString());

        if (leagueId != null) {
            builder.addString("leagueId", leagueId);
        }

        var parameters = builder.toJobParameters();

        assertThatThrownBy(() -> jobOperator.start(job, parameters))
                .isInstanceOf(InvalidJobParametersException.class)
                .hasMessage("leagueId는 필수입니다.");

        verifyNoInteractions(sportsDbClient);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    void 시즌이_없으면_배치_실행을_거부한다(String season) {
        var builder = new JobParametersBuilder()
                .addString("leagueId", "4328")
                .addString("run.id", UUID.randomUUID().toString());

        if (season != null) {
            builder.addString("season", season);
        }

        var parameters = builder.toJobParameters();

        assertThatThrownBy(() -> jobOperator.start(job, parameters))
                .isInstanceOf(InvalidJobParametersException.class)
                .hasMessage("season은 필수입니다.");

        verifyNoInteractions(sportsDbClient);
    }
}