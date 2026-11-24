package com.codeit.sb13.playlist_for_everyone.domain.content.collector.batch;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.job.parameters.JobParameters;
import org.springframework.batch.core.launch.JobOperator;
import org.springframework.batch.core.repository.JobRepository;

import java.sql.SQLException;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class CollectionJobLauncherTest {

    private JobOperator jobOperator;
    private JobRepository jobRepository;
    private Job tmdbJob;
    private Job sportsDbJob;
    private CollectionJobLock jobLock;
    private CollectionJobLock.Handle handle;
    private CollectionJobLauncher launcher;
    private CollectionExecutionRecoveryPolicy recoveryPolicy;

    @BeforeEach
    void setUp() throws Exception {
        jobOperator = mock(JobOperator.class);
        jobRepository = mock(JobRepository.class);
        tmdbJob = mock(Job.class);
        sportsDbJob = mock(Job.class);
        jobLock = mock(CollectionJobLock.class);
        handle = mock(CollectionJobLock.Handle.class);
        recoveryPolicy = mock(CollectionExecutionRecoveryPolicy.class);

        when(tmdbJob.getName()).thenReturn("tmdbCollectionJob");
        when(sportsDbJob.getName()).thenReturn("sportsDbCollectionJob");
        when(jobLock.tryAcquire(anyLong())).thenReturn(Optional.of(handle));

        launcher = new CollectionJobLauncher(
                jobOperator,
                tmdbJob,
                sportsDbJob,
                jobRepository,
                jobLock,
                recoveryPolicy
        );
    }

    @Test
    void 수집할_페이지_수를_전달해_TMDB_배치를_실행한다() throws Exception {
        launcher.startTmdb(5L);

        var captor = ArgumentCaptor.forClass(JobParameters.class);
        verify(jobOperator).start(eq(tmdbJob), captor.capture());

        var parameters = captor.getValue();
        assertThat(parameters.getLong("maxPages")).isEqualTo(5L);
        assertThat(parameters.getString("run.id")).isNotBlank();

        verify(handle).close();
    }

    @Test
    void 리그와_시즌을_전달해_스포츠_배치를_실행한다() throws Exception {
        launcher.startSportsDb("4328", "2014-2015");

        var captor = ArgumentCaptor.forClass(JobParameters.class);
        verify(jobOperator).start(eq(sportsDbJob), captor.capture());

        var parameters = captor.getValue();
        assertThat(parameters.getString("leagueId")).isEqualTo("4328");
        assertThat(parameters.getString("season")).isEqualTo("2014-2015");
        assertThat(parameters.getString("run.id")).isNotBlank();

        verify(handle).close();
    }

    @ParameterizedTest
    @ValueSource(strings = {"tmdb", "sportsdb"})
    void 잠금을_얻지_못하면_실행과_복구를_건너뛴다(String provider) throws Exception {
        when(jobLock.tryAcquire(anyLong())).thenReturn(Optional.empty());

        start(provider);

        long lockKey = "tmdb".equals(provider) ? 254_001L : 254_002L;
        verify(jobLock).tryAcquire(lockKey);

        verifyNoInteractions(jobRepository, jobOperator, handle);
    }

    @ParameterizedTest
    @ValueSource(strings = {"tmdb", "sportsdb"})
    void 복구가_허용되지_않으면_복구와_새_실행을_보류한다(
            String provider
    ) throws Exception {
        Job job = "tmdb".equals(provider) ? tmdbJob : sportsDbJob;
        var execution = mock(JobExecution.class);

        when(jobRepository.findRunningJobExecutions(job.getName()))
                .thenReturn(Set.of(execution));
        when(recoveryPolicy.canRecover(execution)).thenReturn(false);

        start(provider);

        verify(recoveryPolicy).canRecover(execution);
        verifyNoInteractions(jobOperator);
        verify(handle).close();
    }

    @ParameterizedTest
    @ValueSource(strings = {"tmdb", "sportsdb"})
    void 배치_실행에서_예외가_발생해도_잠금을_반납한다(String provider) throws Exception {
        Job job = "tmdb".equals(provider) ? tmdbJob : sportsDbJob;

        when(jobOperator.start(eq(job), any(JobParameters.class))).thenThrow(new IllegalStateException("수집 실행 실패"));

        assertThatThrownBy(() -> start(provider)).isInstanceOf(IllegalStateException.class).hasMessage("수집 실행 실패");

        verify(handle).close();
    }

    @ParameterizedTest
    @ValueSource(strings = {"tmdb", "sportsdb"})
    void 잠금_획득에서_오류가_발생하면_복구하거나_실행하지_않는다(String provider) throws Exception {
        var failure = new SQLException("잠금 연결 실패");

        when(jobLock.tryAcquire(anyLong())).thenThrow(failure);

        assertThatThrownBy(() -> start(provider)).isSameAs(failure);

        verifyNoInteractions(jobRepository, jobOperator, handle);
    }

    @ParameterizedTest
    @ValueSource(strings = {"tmdb", "sportsdb"})
    void 이전_실행_복구에_실패하면_새로_실행하지_않고_잠금을_반납한다(String provider) throws Exception {
        Job job = "tmdb".equals(provider) ? tmdbJob : sportsDbJob;
        var execution = mock(JobExecution.class);
        var failure = new IllegalStateException("수집 실행 복구 실패");

        when(jobRepository.findRunningJobExecutions(job.getName())).thenReturn(Set.of(execution));
        when(recoveryPolicy.canRecover(execution)).thenReturn(true);

        doThrow(failure).when(jobOperator).recover(execution);

        assertThatThrownBy(() -> start(provider)).isSameAs(failure);

        var order = inOrder(jobOperator, handle);
        order.verify(jobOperator).recover(execution);
        order.verify(handle).close();

        verifyNoMoreInteractions(jobOperator);
    }

    @ParameterizedTest
    @ValueSource(strings = {"tmdb", "sportsdb"})
    void 종료가_확인된_이전_작업을_복구한_뒤_재시작한다(
            String provider
    ) throws Exception {
        Job job = "tmdb".equals(provider) ? tmdbJob : sportsDbJob;
        var execution = mock(JobExecution.class);
        var recovered = mock(JobExecution.class);

        when(jobRepository.findRunningJobExecutions(job.getName()))
                .thenReturn(Set.of(execution));
        when(recoveryPolicy.canRecover(execution)).thenReturn(true);
        when(jobOperator.recover(execution)).thenReturn(recovered);

        start(provider);

        var order = inOrder(recoveryPolicy, jobOperator, handle);

        order.verify(recoveryPolicy).canRecover(execution);
        order.verify(jobOperator).recover(execution);
        order.verify(jobOperator).restart(recovered);
        order.verify(handle).close();

        verify(jobOperator, never())
                .start(any(Job.class), any(JobParameters.class));
    }

    @ParameterizedTest
    @ValueSource(strings = {"tmdb", "sportsdb"})
    void 복구할_수_없는_실행이_하나라도_있으면_모든_복구를_보류한다(
            String provider
    ) throws Exception {
        Job job = "tmdb".equals(provider) ? tmdbJob : sportsDbJob;
        var stoppedExecution = mock(JobExecution.class);
        var runningExecution = mock(JobExecution.class);

        var executions = new LinkedHashSet<>(List.of(
                stoppedExecution,
                runningExecution
        ));

        when(jobRepository.findRunningJobExecutions(job.getName()))
                .thenReturn(executions);
        when(recoveryPolicy.canRecover(stoppedExecution)).thenReturn(true);
        when(recoveryPolicy.canRecover(runningExecution)).thenReturn(false);

        start(provider);

        var order = inOrder(recoveryPolicy);
        order.verify(recoveryPolicy).canRecover(stoppedExecution);
        order.verify(recoveryPolicy).canRecover(runningExecution);

        verifyNoInteractions(jobOperator);
        verify(handle).close();
    }

    @ParameterizedTest
    @ValueSource(strings = {"tmdb", "sportsdb"})
    void 태스크_상태_조회에_실패하면_실행하지_않고_잠금을_반납한다(
            String provider
    ) throws Exception {
        Job job = "tmdb".equals(provider) ? tmdbJob : sportsDbJob;
        var execution = mock(JobExecution.class);
        var failure = new IllegalStateException("ECS 태스크 조회 실패");

        when(jobRepository.findRunningJobExecutions(job.getName()))
                .thenReturn(Set.of(execution));
        when(recoveryPolicy.canRecover(execution)).thenThrow(failure);

        assertThatThrownBy(() -> start(provider))
                .isSameAs(failure);

        verifyNoInteractions(jobOperator);
        verify(handle).close();
    }

    @ParameterizedTest
    @ValueSource(strings = {"tmdb", "sportsdb"})
    void 재시작에_실패하면_새_작업을_실행하지_않고_잠금을_반납한다(
            String provider
    ) throws Exception {
        Job job = "tmdb".equals(provider) ? tmdbJob : sportsDbJob;
        var execution = mock(JobExecution.class);
        var recovered = mock(JobExecution.class);
        var failure = new IllegalStateException("수집 작업 재시작 실패");

        when(jobRepository.findRunningJobExecutions(job.getName()))
                .thenReturn(Set.of(execution));
        when(recoveryPolicy.canRecover(execution)).thenReturn(true);
        when(jobOperator.recover(execution)).thenReturn(recovered);
        when(jobOperator.restart(recovered)).thenThrow(failure);

        assertThatThrownBy(() -> start(provider))
                .isSameAs(failure);

        var order = inOrder(jobOperator, handle);
        order.verify(jobOperator).recover(execution);
        order.verify(jobOperator).restart(recovered);
        order.verify(handle).close();

        verifyNoMoreInteractions(jobOperator);
    }

    private void start(String provider) throws Exception {
        if ("tmdb".equals(provider)) {
            launcher.startTmdb(5L);
        } else {
            launcher.startSportsDb("4328", "2014-2015");
        }
    }
}