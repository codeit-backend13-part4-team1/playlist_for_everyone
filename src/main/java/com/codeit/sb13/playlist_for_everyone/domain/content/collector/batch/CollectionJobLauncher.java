package com.codeit.sb13.playlist_for_everyone.domain.content.collector.batch;

import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.parameters.JobParametersBuilder;
import org.springframework.batch.core.launch.JobOperator;
import org.springframework.batch.core.repository.JobRepository;

import java.util.UUID;

public class CollectionJobLauncher {

    private final JobOperator jobOperator;
    private final Job tmdbCollectionJob;
    private final Job sportsDbCollectionJob;
    private final JobRepository jobRepository;

    public CollectionJobLauncher(
            JobOperator jobOperator,
            Job tmdbCollectionJob,
            Job sportsDbCollectionJob,
            JobRepository jobRepository
    ) {
        this.jobOperator = jobOperator;
        this.tmdbCollectionJob = tmdbCollectionJob;
        this.sportsDbCollectionJob = sportsDbCollectionJob;
        this.jobRepository = jobRepository;
    }

    public void startTmdb(long maxPages) throws Exception {
        if (!jobRepository.findRunningJobExecutions(
                tmdbCollectionJob.getName()
        ).isEmpty()) {
            return;
        }

        var parameters = new JobParametersBuilder()
                .addLong("maxPages", maxPages)
                .addString("run.id", UUID.randomUUID().toString())
                .toJobParameters();

        jobOperator.start(tmdbCollectionJob, parameters);
    }

    public void startSportsDb(String leagueId, String season) throws Exception {
        if (!jobRepository.findRunningJobExecutions(
                sportsDbCollectionJob.getName()
        ).isEmpty()) {
            return;
        }

        var parameters = new JobParametersBuilder()
                .addString("leagueId", leagueId)
                .addString("season", season)
                .addString("run.id", UUID.randomUUID().toString())
                .toJobParameters();

        jobOperator.start(sportsDbCollectionJob, parameters);
    }
}