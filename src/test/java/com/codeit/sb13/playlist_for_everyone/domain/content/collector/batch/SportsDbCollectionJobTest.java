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
import org.springframework.web.client.ResourceAccessException;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;
import static org.mockito.Mockito.*;

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

    @Test
    void 제목이_없는_경기는_건너뛰고_정상_경기는_저장한다() throws Exception {
        var first = new SportsDbEventResponse(
                "442101", "첫 번째 경기", "Soccer",
                "English Premier League", "2014-2015",
                "2014-08-16", null
        );
        var invalid = new SportsDbEventResponse(
                "442102", "   ", "Soccer",
                "English Premier League", "2014-2015",
                "2014-08-17", null
        );
        var last = new SportsDbEventResponse(
                "442103", "마지막 경기", "Soccer",
                "English Premier League", "2014-2015",
                "2014-08-18", null
        );

        when(sportsDbClient.getEventsBySeason("4328", "2014-2015"))
                .thenReturn(new SportsDbEventsResponse(
                        List.of(first, invalid, last)
                ));

        var parameters = new JobParametersBuilder()
                .addString("leagueId", "4328")
                .addString("season", "2014-2015")
                .addString("run.id", UUID.randomUUID().toString())
                .toJobParameters();

        var execution = jobOperator.start(job, parameters);

        assertThat(execution.getStatus())
                .isEqualTo(BatchStatus.COMPLETED);

        assertThat(contentRepository.findByProviderAndTypeAndProviderItemId(
                ContentSource.THE_SPORTS_DB,
                ContentType.sport,
                "442101"
        )).isPresent();

        assertThat(contentRepository.findByProviderAndTypeAndProviderItemId(
                ContentSource.THE_SPORTS_DB,
                ContentType.sport,
                "442102"
        )).isEmpty();

        assertThat(contentRepository.findByProviderAndTypeAndProviderItemId(
                ContentSource.THE_SPORTS_DB,
                ContentType.sport,
                "442103"
        )).isPresent();

        var stepExecution = execution.getStepExecutions().iterator().next();

        assertThat(stepExecution.getWriteSkipCount()).isEqualTo(1);
    }

    @Test
    void 잘못된_경기가_건너뛰기_제한을_초과하면_배치가_실패한다()
            throws Exception {
        var events = new ArrayList<SportsDbEventResponse>();

        events.add(new SportsDbEventResponse(
                "443001", "첫 번째 정상 경기", "Soccer",
                "English Premier League", "2014-2015",
                "2014-08-16", null
        ));

        for (int i = 0; i < 11; i++) {
            events.add(new SportsDbEventResponse(
                    String.valueOf(443010 + i), "   ", "Soccer",
                    "English Premier League", "2014-2015",
                    "2014-08-17", null
            ));
        }

        events.add(new SportsDbEventResponse(
                "443099", "마지막 정상 경기", "Soccer",
                "English Premier League", "2014-2015",
                "2014-08-18", null
        ));

        when(sportsDbClient.getEventsBySeason("4328", "2014-2015"))
                .thenReturn(new SportsDbEventsResponse(events));

        var parameters = new JobParametersBuilder()
                .addString("leagueId", "4328")
                .addString("season", "2014-2015")
                .addString("run.id", UUID.randomUUID().toString())
                .toJobParameters();

        var execution = jobOperator.start(job, parameters);

        assertThat(execution.getStatus())
                .isEqualTo(BatchStatus.FAILED);

        assertThat(contentRepository.findByProviderAndTypeAndProviderItemId(
                ContentSource.THE_SPORTS_DB,
                ContentType.sport,
                "443001"
        )).isPresent();

        assertThat(contentRepository.findByProviderAndTypeAndProviderItemId(
                ContentSource.THE_SPORTS_DB,
                ContentType.sport,
                "443099"
        )).isEmpty();

        var stepExecution = execution.getStepExecutions().iterator().next();

        assertThat(stepExecution.getWriteSkipCount()).isEqualTo(10);
    }

    @Test
    void API_조회_실패_후_재시작하면_경기가_저장된다() throws Exception {
        var event = new SportsDbEventResponse(
                "444001", "재시작 테스트 경기", "Soccer",
                "English Premier League", "2014-2015",
                "2014-08-16", null
        );

        when(sportsDbClient.getEventsBySeason("4328", "2014-2015"))
                .thenThrow(new ResourceAccessException("테스트용 연결 실패"))
                .thenReturn(new SportsDbEventsResponse(List.of(event)));

        var parameters = new JobParametersBuilder()
                .addString("leagueId", "4328")
                .addString("season", "2014-2015")
                .addString("run.id", UUID.randomUUID().toString())
                .toJobParameters();

        var failedExecution = jobOperator.start(job, parameters);

        assertThat(failedExecution.getStatus())
                .isEqualTo(BatchStatus.FAILED);

        assertThat(contentRepository.findByProviderAndTypeAndProviderItemId(
                ContentSource.THE_SPORTS_DB,
                ContentType.sport,
                "444001"
        )).isEmpty();

        var restartedExecution = jobOperator.restart(failedExecution);

        assertThat(restartedExecution.getStatus())
                .isEqualTo(BatchStatus.COMPLETED);

        assertThat(restartedExecution.getId())
                .isNotEqualTo(failedExecution.getId());

        var saved = contentRepository.findByProviderAndTypeAndProviderItemId(
                ContentSource.THE_SPORTS_DB,
                ContentType.sport,
                "444001"
        );

        assertThat(saved).isPresent();
        assertThat(saved.orElseThrow().getTitle())
                .isEqualTo("재시작 테스트 경기");

        verify(sportsDbClient, times(2))
                .getEventsBySeason("4328", "2014-2015");
    }

    @Test
    void 중간에_실패한_배치를_재시작하면_기존_저장을_유지하고_이어서_저장한다()
            throws Exception {
        var first = new SportsDbEventResponse(
                "445001", "첫 번째 경기", "Soccer",
                "English Premier League", "2014-2015",
                "2014-08-16", null
        );
        var invalid = new SportsDbEventResponse(
                null, "두 번째 경기", "Soccer",
                "English Premier League", "2014-2015",
                "2014-08-17", null
        );
        var corrected = new SportsDbEventResponse(
                "445002", "두 번째 경기", "Soccer",
                "English Premier League", "2014-2015",
                "2014-08-17", null
        );

        when(sportsDbClient.getEventsBySeason("4328", "2014-2015"))
                .thenReturn(new SportsDbEventsResponse(List.of(first, invalid)))
                .thenReturn(new SportsDbEventsResponse(List.of(first, corrected)));

        var parameters = new JobParametersBuilder()
                .addString("leagueId", "4328")
                .addString("season", "2014-2015")
                .addString("run.id", UUID.randomUUID().toString())
                .toJobParameters();

        var failedExecution = jobOperator.start(job, parameters);

        assertThat(failedExecution.getStatus())
                .isEqualTo(BatchStatus.FAILED);

        var savedFirst = contentRepository.findByProviderAndTypeAndProviderItemId(
                ContentSource.THE_SPORTS_DB,
                ContentType.sport,
                "445001"
        );

        assertThat(savedFirst).isPresent();
        var firstId = savedFirst.orElseThrow().getId();

        assertThat(contentRepository.findByProviderAndTypeAndProviderItemId(
                ContentSource.THE_SPORTS_DB,
                ContentType.sport,
                "445002"
        )).isEmpty();

        var restartedExecution = jobOperator.restart(failedExecution);

        assertThat(restartedExecution.getStatus())
                .isEqualTo(BatchStatus.COMPLETED);

        var firstAfterRestart =
                contentRepository.findByProviderAndTypeAndProviderItemId(
                        ContentSource.THE_SPORTS_DB,
                        ContentType.sport,
                        "445001"
                );

        assertThat(firstAfterRestart).isPresent();
        assertThat(firstAfterRestart.orElseThrow().getId()).isEqualTo(firstId);

        var savedSecond = contentRepository.findByProviderAndTypeAndProviderItemId(
                ContentSource.THE_SPORTS_DB,
                ContentType.sport,
                "445002"
        );

        assertThat(savedSecond).isPresent();
        assertThat(savedSecond.orElseThrow().getTitle())
                .isEqualTo("두 번째 경기");

        var restartedStep =
                restartedExecution.getStepExecutions().iterator().next();

        assertThat(restartedStep.getReadCount()).isEqualTo(1);

        verify(sportsDbClient, times(2))
                .getEventsBySeason("4328", "2014-2015");
    }
}