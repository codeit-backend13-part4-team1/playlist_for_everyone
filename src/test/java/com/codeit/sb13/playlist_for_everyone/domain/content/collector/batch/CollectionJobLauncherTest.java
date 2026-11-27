package com.codeit.sb13.playlist_for_everyone.domain.content.collector.batch;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.job.JobInstance;
import org.springframework.batch.core.job.parameters.JobParameters;
import org.springframework.batch.core.launch.JobOperator;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.infrastructure.item.ExecutionContext;

import java.sql.SQLException;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

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

    private void startScheduled(CollectionJobLauncher target, String provider, Instant scheduledAt) throws Exception {
        if ("tmdb".equals(provider)) {
            target.startScheduledTmdb(5L, scheduledAt);
        } else {
            target.startScheduledSportsDb("4328", "2014-2015", scheduledAt);
        }
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

        when(execution.getExecutionContext())
                .thenReturn(new ExecutionContext());
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
    @CsvSource({
            "tmdb, false",
            "tmdb, true",
            "sportsdb, false",
            "sportsdb, true"
    })
    void 종료가_확인된_이전_작업을_복구한_뒤_재시작한다(String provider, boolean scheduled) throws Exception {
        Job job = "tmdb".equals(provider) ? tmdbJob : sportsDbJob;
        var execution = mock(JobExecution.class);
        var recovered = mock(JobExecution.class);

        when(execution.getExecutionContext())
                .thenReturn(new ExecutionContext());
        when(jobRepository.findRunningJobExecutions(job.getName()))
                .thenReturn(Set.of(execution));
        when(jobRepository.getJobInstance(
                eq(job.getName()),
                any(JobParameters.class)
        )).thenReturn(mock(JobInstance.class));
        when(recoveryPolicy.canRecover(execution)).thenReturn(true);
        when(jobOperator.recover(execution)).thenReturn(recovered);

        if (scheduled) {
            startScheduled(launcher, provider, Instant.parse("2026-11-26T15:00:00Z"));
        } else {
            start(provider);
        }

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

        when(execution.getExecutionContext())
                .thenReturn(new ExecutionContext());
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

    @ParameterizedTest
    @ValueSource(strings = {"tmdb", "sportsdb"})
    void 재시작_대기_표시가_없는_실패_이력은_자동_재개하지_않는다(
            String provider
    ) throws Exception {
        Job job = "tmdb".equals(provider) ? tmdbJob : sportsDbJob;
        var instance = mock(JobInstance.class);
        var failedExecution = mock(JobExecution.class);

        when(jobRepository.getJobInstances(eq(job.getName()), eq(0), anyInt()))
                .thenReturn(List.of(instance));
        when(jobRepository.getLastJobExecution(instance)).thenReturn(failedExecution);
        when(failedExecution.getStatus()).thenReturn(BatchStatus.FAILED);
        when(failedExecution.getExecutionContext()).thenReturn(new ExecutionContext());

        start(provider);

        verify(jobOperator).start(eq(job), any(JobParameters.class));
        verify(jobOperator, never()).recover(any(JobExecution.class));
        verify(jobOperator, never()).restart(any(JobExecution.class));
        verifyNoInteractions(recoveryPolicy);
        verify(handle).close();
    }

    private void start(String provider) throws Exception {
        if ("tmdb".equals(provider)) {
            launcher.startTmdb(5L);
        } else {
            launcher.startSportsDb("4328", "2014-2015");
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"tmdb", "sportsdb"})
    void 같은_예약을_두_서버가_순차_처리해도_한_번만_실행한다(String provider) throws Exception {
        Job job = "tmdb".equals(provider) ? tmdbJob : sportsDbJob;
        var scheduledAt = Instant.parse("2026-11-26T15:00:00Z");
        var storedParameters = new AtomicReference<JobParameters>();
        var storedInstance = mock(JobInstance.class);

        when(jobOperator.start(eq(job), any(JobParameters.class)))
                .thenAnswer(invocation -> {
                    storedParameters.set(invocation.getArgument(1));
                    return mock(JobExecution.class);
                });

        when(jobRepository.getJobInstance(
                eq(job.getName()),
                any(JobParameters.class)
        )).thenAnswer(invocation -> {
            JobParameters parameters = invocation.getArgument(1);

            return parameters.equals(storedParameters.get())
                    ? storedInstance
                    : null;
        });

        var secondLauncher = new CollectionJobLauncher(
                jobOperator,
                tmdbJob,
                sportsDbJob,
                jobRepository,
                jobLock,
                recoveryPolicy
        );

        startScheduled(launcher, provider, scheduledAt);
        startScheduled(secondLauncher, provider, scheduledAt);

        verify(jobOperator, times(1))
                .start(eq(job), any(JobParameters.class));
        verify(handle, times(2)).close();
    }

    @ParameterizedTest
    @ValueSource(strings = {"tmdb", "sportsdb"})
    void 다음_예약_회차는_새로운_배치로_실행한다(String provider) throws Exception {
        Job job = "tmdb".equals(provider) ? tmdbJob : sportsDbJob;
        var firstScheduledAt = Instant.parse("2026-11-26T15:00:00Z");
        var nextScheduledAt = Instant.parse("2026-11-27T15:00:00Z");

        startScheduled(launcher, provider, firstScheduledAt);
        startScheduled(launcher, provider, nextScheduledAt);

        var captor = ArgumentCaptor.forClass(JobParameters.class);
        verify(jobOperator, times(2)).start(eq(job), captor.capture());

        assertThat(captor.getAllValues())
                .extracting(parameters -> parameters.getString("run.id"))
                .containsExactly(
                        "scheduled-" + firstScheduledAt,
                        "scheduled-" + nextScheduledAt
                );

        verify(handle, times(2)).close();
    }

    @ParameterizedTest
    @ValueSource(strings = {"tmdb", "sportsdb"})
    void 수동_수집은_호출할_때마다_별도로_실행한다(String provider) throws Exception {
        Job job = "tmdb".equals(provider) ? tmdbJob : sportsDbJob;

        start(provider);
        start(provider);

        var captor = ArgumentCaptor.forClass(JobParameters.class);
        verify(jobOperator, times(2)).start(eq(job), captor.capture());

        assertThat(captor.getAllValues())
                .extracting(parameters -> parameters.getString("run.id"))
                .doesNotHaveDuplicates();

        verify(jobRepository, never())
                .getJobInstance(anyString(), any(JobParameters.class));
        verify(handle, times(2)).close();
    }
}