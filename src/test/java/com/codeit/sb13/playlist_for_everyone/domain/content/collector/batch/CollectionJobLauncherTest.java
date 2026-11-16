package com.codeit.sb13.playlist_for_everyone.domain.content.collector.batch;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.parameters.JobParameters;
import org.springframework.batch.core.launch.JobOperator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class CollectionJobLauncherTest {

    @Test
    void 수집할_페이지_수를_전달해_TMDB_배치를_실행한다() throws Exception {
        var jobOperator = mock(JobOperator.class);
        var tmdbJob = mock(Job.class);
        var launcher = new CollectionJobLauncher(jobOperator, tmdbJob, sportsDbJob);

        launcher.startTmdb(5L);

        var captor = ArgumentCaptor.forClass(JobParameters.class);
        verify(jobOperator).start(eq(tmdbJob), captor.capture());

        var parameters = captor.getValue();
        assertThat(parameters.getLong("maxPages")).isEqualTo(5L);
        assertThat(parameters.getString("run.id")).isNotBlank();
    }

    @Test
    void 리그와_시즌을_전달해_스포츠_배치를_실행한다() throws Exception {
        var jobOperator = mock(JobOperator.class);
        var tmdbJob = mock(Job.class);
        var sportsDbJob = mock(Job.class);
        var launcher = new CollectionJobLauncher(
                jobOperator, tmdbJob, sportsDbJob
        );

        launcher.startSportsDb("4328", "2014-2015");

        var captor = ArgumentCaptor.forClass(JobParameters.class);
        verify(jobOperator).start(eq(sportsDbJob), captor.capture());

        var parameters = captor.getValue();
        assertThat(parameters.getString("leagueId")).isEqualTo("4328");
        assertThat(parameters.getString("season")).isEqualTo("2014-2015");
        assertThat(parameters.getString("run.id")).isNotBlank();
    }
}