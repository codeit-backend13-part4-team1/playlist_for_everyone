package com.codeit.sb13.playlist_for_everyone.domain.content.collector.batch;

import com.codeit.sb13.playlist_for_everyone.domain.content.collector.config.CollectionScheduleProperties;

public class CollectionScheduler {

    private final CollectionJobLauncher launcher;
    private final CollectionScheduleProperties properties;

    public CollectionScheduler(
            CollectionJobLauncher launcher,
            CollectionScheduleProperties properties
    ) {
        this.launcher = launcher;
        this.properties = properties;
    }

    public void collectTmdb() throws Exception {
    }

    public void collectSportsDb() throws Exception {
    }
}