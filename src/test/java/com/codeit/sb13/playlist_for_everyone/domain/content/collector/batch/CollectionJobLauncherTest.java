package com.codeit.sb13.playlist_for_everyone.domain.content.collector.batch;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.parameters.JobParameters;
import org.springframework.batch.core.launch.JobOperator;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.job.JobExecution;

import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verifyNoInteractions;

class CollectionJobLauncherTest {

    @Test
    void 수집할_페이지_수를_전달해_TMDB_배치를_실행한다() throws Exception {
        var jobOperator = mock(JobOperator.class);
        var tmdbJob = mock(Job.class);
        var sportsDbJob = mock(Job.class);
        var jobRepository = mock(JobRepository.class);
        var launcher = new CollectionJobLauncher(jobOperator, tmdbJob, sportsDbJob, jobRepository);

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
        var jobRepository = mock(JobRepository.class);
        var launcher = new CollectionJobLauncher(jobOperator, tmdbJob, sportsDbJob, jobRepository);

        launcher.startSportsDb("4328", "2014-2015");

        var captor = ArgumentCaptor.forClass(JobParameters.class);
        verify(jobOperator).start(eq(sportsDbJob), captor.capture());

        var parameters = captor.getValue();
        assertThat(parameters.getString("leagueId")).isEqualTo("4328");
        assertThat(parameters.getString("season")).isEqualTo("2014-2015");
        assertThat(parameters.getString("run.id")).isNotBlank();
    }

    @Test
    void TMDB_배치가_실행_중이면_새로_실행하지_않는다() throws Exception {
        var jobOperator = mock(JobOperator.class);
        var jobRepository = mock(JobRepository.class);
        var tmdbJob = mock(Job.class);
        var sportsDbJob = mock(Job.class);

        when(tmdbJob.getName()).thenReturn("tmdbCollectionJob");
        when(jobRepository.findRunningJobExecutions("tmdbCollectionJob"))
                .thenReturn(Set.of(mock(JobExecution.class)));

        var launcher = new CollectionJobLauncher(
                jobOperator, tmdbJob, sportsDbJob, jobRepository
        );

        launcher.startTmdb(5L);

        verify(jobRepository).findRunningJobExecutions("tmdbCollectionJob");
        verifyNoInteractions(jobOperator);
    }

    @Test
    void 스포츠_배치가_실행_중이면_새로_실행하지_않는다() throws Exception {
        var jobOperator = mock(JobOperator.class);
        var jobRepository = mock(JobRepository.class);
        var tmdbJob = mock(Job.class);
        var sportsDbJob = mock(Job.class);

        when(sportsDbJob.getName()).thenReturn("sportsDbCollectionJob");
        when(jobRepository.findRunningJobExecutions("sportsDbCollectionJob"))
                .thenReturn(Set.of(mock(JobExecution.class)));

        var launcher = new CollectionJobLauncher(
                jobOperator, tmdbJob, sportsDbJob, jobRepository
        );

        launcher.startSportsDb("4328", "2014-2015");

        verify(jobRepository).findRunningJobExecutions("sportsDbCollectionJob");
        verifyNoInteractions(jobOperator);
    }

    @Test
    void 이전에_남은_두_수집_작업의_실행을_복구한다() {
        var jobOperator = mock(JobOperator.class);
        var jobRepository = mock(JobRepository.class);
        var tmdbJob = mock(Job.class);
        var sportsDbJob = mock(Job.class);
        var tmdbExecution = mock(JobExecution.class);
        var sportsDbExecution = mock(JobExecution.class);

        when(tmdbJob.getName()).thenReturn("tmdbCollectionJob");
        when(sportsDbJob.getName()).thenReturn("sportsDbCollectionJob");

        when(jobRepository.findRunningJobExecutions("tmdbCollectionJob"))
                .thenReturn(Set.of(tmdbExecution));
        when(jobRepository.findRunningJobExecutions("sportsDbCollectionJob"))
                .thenReturn(Set.of(sportsDbExecution));

        var launcher = new CollectionJobLauncher(
                jobOperator, tmdbJob, sportsDbJob, jobRepository
        );

        launcher.recoverInterruptedExecutions();

        verify(jobOperator).recover(tmdbExecution);
        verify(jobOperator).recover(sportsDbExecution);
    }

    @ParameterizedTest
    @ValueSource(strings = {"tmdb", "sportsdb"})
    void 잠금을_얻지_못하면_실행과_복구를_건너뛴다(String provider) throws Exception {
        var jobOperator = mock(JobOperator.class);
        var jobRepository = mock(JobRepository.class);
        var tmdbJob = mock(Job.class);
        var sportsDbJob = mock(Job.class);
        var jobLock = mock(CollectionJobLock.class);

        when(jobLock.tryAcquire(anyLong())).thenReturn(Optional.empty());

        var launcher = new CollectionJobLauncher(
                jobOperator,
                tmdbJob,
                sportsDbJob,
                jobRepository,
                jobLock
        );

        if ("tmdb".equals(provider)) {
            launcher.startTmdb(5L);
            verify(jobLock).tryAcquire(254_001L);
        } else {
            launcher.startSportsDb("4328", "2014-2015");
            verify(jobLock).tryAcquire(254_002L);
        }

        verifyNoInteractions(jobRepository, jobOperator);
    }
}