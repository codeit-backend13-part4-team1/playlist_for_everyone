package com.codeit.sb13.playlist_for_everyone.domain.content.collector.batch;

import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.launch.JobOperator;

public class CollectionJobLauncher {

    private final JobOperator jobOperator;
    private final Job tmdbCollectionJob;

    public CollectionJobLauncher(
            JobOperator jobOperator,
            Job tmdbCollectionJob
    ) {
        this.jobOperator = jobOperator;
        this.tmdbCollectionJob = tmdbCollectionJob;
    }

    public void startTmdb(long maxPages) throws Exception {
    }
}