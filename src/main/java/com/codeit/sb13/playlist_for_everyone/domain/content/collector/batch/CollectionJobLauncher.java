package com.codeit.sb13.playlist_for_everyone.domain.content.collector.batch;

import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.job.parameters.JobParameters;
import org.springframework.batch.core.job.parameters.JobParametersBuilder;
import org.springframework.batch.core.launch.JobOperator;
import org.springframework.batch.core.repository.JobRepository;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public class CollectionJobLauncher {

    private static final long TMDB_LOCK_KEY = 254_001L;
    private static final long SPORTS_DB_LOCK_KEY = 254_002L;

    private final JobOperator jobOperator;
    private final Job tmdbCollectionJob;
    private final Job sportsDbCollectionJob;
    private final JobRepository jobRepository;
    private final CollectionJobLock jobLock;
    private final CollectionExecutionRecoveryPolicy recoveryPolicy;

    public CollectionJobLauncher(JobOperator jobOperator, Job tmdbCollectionJob, Job sportsDbCollectionJob, JobRepository jobRepository, CollectionJobLock jobLock, CollectionExecutionRecoveryPolicy recoveryPolicy) {
        this.jobOperator = jobOperator;
        this.tmdbCollectionJob = tmdbCollectionJob;
        this.sportsDbCollectionJob = sportsDbCollectionJob;
        this.jobRepository = jobRepository;
        this.jobLock = jobLock;
        this.recoveryPolicy = recoveryPolicy;
    }

    public void startTmdb(long maxPages) throws Exception {
        var parameters = new JobParametersBuilder().addLong("maxPages", maxPages).addString("run.id", UUID.randomUUID().toString()).toJobParameters();

        startWithLock(tmdbCollectionJob, TMDB_LOCK_KEY, parameters, false);
    }

    public void startSportsDb(String leagueId, String season) throws Exception {
        var parameters = new JobParametersBuilder().addString("leagueId", leagueId).addString("season", season).addString("run.id", UUID.randomUUID().toString()).toJobParameters();

        startWithLock(sportsDbCollectionJob, SPORTS_DB_LOCK_KEY, parameters, false);
    }

    public void startScheduledTmdb(long maxPages, Instant scheduledAt) throws Exception {
        Objects.requireNonNull(scheduledAt, "예약 시각은 필수입니다.");

        var parameters = new JobParametersBuilder().addLong("maxPages", maxPages, false).addString("run.id", "scheduled-" + scheduledAt).toJobParameters();

        startWithLock(tmdbCollectionJob, TMDB_LOCK_KEY, parameters, true);
    }

    public void startScheduledSportsDb(String leagueId, String season, Instant scheduledAt) throws Exception {
        Objects.requireNonNull(scheduledAt, "예약 시각은 필수입니다.");

        var parameters = new JobParametersBuilder().addString("leagueId", leagueId, false).addString("season", season, false).addString("run.id", "scheduled-" + scheduledAt).toJobParameters();

        startWithLock(sportsDbCollectionJob, SPORTS_DB_LOCK_KEY, parameters, true);
    }

    /**
     * 잠금 안에서 신규 실행 또는 중단된 작업 재시작
     */
    private void startWithLock(Job job, long lockKey, JobParameters parameters, boolean scheduled) throws Exception {
        var acquired = jobLock.tryAcquire(lockKey);

        if (acquired.isEmpty()) {
            return;
        }

        try (var handle = acquired.get()) {
            var executions = findExecutionsToResume(job);

            if (executions.isEmpty()) {
                if (scheduled && jobRepository.getJobInstance(job.getName(), parameters) != null) {
                    return;
                }

                jobOperator.start(job, parameters);
                return;
            }

            for (var execution : executions) {
                if (!recoveryPolicy.canRecover(execution)) {
                    return;
                }
            }

            for (var execution : executions) {
                execution.getExecutionContext().put(CollectionExecutionRecoveryPolicy.RESTART_PENDING_KEY, true);
                jobRepository.updateExecutionContext(execution);

                var recovered = jobOperator.recover(execution);
                jobOperator.restart(recovered);
            }
        }
    }

    /**
     * 실행 중인 작업과 복구 후 재시작을 기다리는 작업 조회
     */
    private List<JobExecution> findExecutionsToResume(Job job) {
        var executions = new ArrayList<>(jobRepository.findRunningJobExecutions(job.getName()));

        int pageSize = 100;
        int offset = 0;

        while (true) {
            var instances = jobRepository.getJobInstances(job.getName(), offset, pageSize);

            for (var instance : instances) {
                var latest = jobRepository.getLastJobExecution(instance);

                if (latest == null || latest.getStatus() != BatchStatus.FAILED) {
                    continue;
                }

                boolean restartPending = Boolean.TRUE.equals(latest.getExecutionContext().get(CollectionExecutionRecoveryPolicy.RESTART_PENDING_KEY));

                if (restartPending) {
                    executions.add(latest);
                }
            }

            if (instances.size() < pageSize) {
                return executions;
            }

            offset += pageSize;
        }
    }
}