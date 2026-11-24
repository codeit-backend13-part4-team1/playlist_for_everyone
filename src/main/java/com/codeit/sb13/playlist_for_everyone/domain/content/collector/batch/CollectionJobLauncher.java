package com.codeit.sb13.playlist_for_everyone.domain.content.collector.batch;

import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.parameters.JobParameters;
import org.springframework.batch.core.job.parameters.JobParametersBuilder;
import org.springframework.batch.core.launch.JobOperator;
import org.springframework.batch.core.repository.JobRepository;

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

    public CollectionJobLauncher(
            JobOperator jobOperator,
            Job tmdbCollectionJob,
            Job sportsDbCollectionJob,
            JobRepository jobRepository,
            CollectionJobLock jobLock,
            CollectionExecutionRecoveryPolicy recoveryPolicy
    ) {
        this.jobOperator = jobOperator;
        this.tmdbCollectionJob = tmdbCollectionJob;
        this.sportsDbCollectionJob = sportsDbCollectionJob;
        this.jobRepository = jobRepository;
        this.jobLock = jobLock;
        this.recoveryPolicy = recoveryPolicy;
    }

    public void startTmdb(long maxPages) throws Exception {
        var parameters = new JobParametersBuilder()
                .addLong("maxPages", maxPages)
                .addString("run.id", UUID.randomUUID().toString())
                .toJobParameters();

        startWithLock(tmdbCollectionJob, TMDB_LOCK_KEY, parameters);
    }

    public void startSportsDb(String leagueId, String season) throws Exception {
        var parameters = new JobParametersBuilder()
                .addString("leagueId", leagueId)
                .addString("season", season)
                .addString("run.id", UUID.randomUUID().toString())
                .toJobParameters();

        startWithLock(sportsDbCollectionJob, SPORTS_DB_LOCK_KEY, parameters);
    }

    /** 잠금을 얻은 경우에만 복구하고 실행 */
    private void startWithLock(
            Job job,
            long lockKey,
            JobParameters parameters
    ) throws Exception {
        var acquired = jobLock.tryAcquire(lockKey);

        if (acquired.isEmpty()) {
            return;
        }

        try (var handle = acquired.get()) {
            if (!recoverInterruptedExecutions(job)) {
                return;
            }

            jobOperator.start(job, parameters);
        }
    }

    private boolean recoverInterruptedExecutions(Job job) {
        var executions = jobRepository.findRunningJobExecutions(job.getName());

        for (var execution : executions) {
            if (!recoveryPolicy.canRecover(execution)) {
                return false;
            }
        }

        for (var execution : executions) {
            jobOperator.recover(execution);
        }

        return true;
    }
}