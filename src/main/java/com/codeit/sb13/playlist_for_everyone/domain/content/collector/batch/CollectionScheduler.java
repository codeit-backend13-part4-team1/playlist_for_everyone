package com.codeit.sb13.playlist_for_everyone.domain.content.collector.batch;

import com.codeit.sb13.playlist_for_everyone.domain.content.collector.config.CollectionScheduleProperties;
import org.springframework.scheduling.annotation.Scheduled;

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

    @Scheduled(
            cron = "${everyone.collection.schedule.tmdb.cron:-}",
            zone = "${everyone.collection.schedule.zone:Asia/Seoul}",
            scheduler = "contentCollectionTaskScheduler"
    )
    public void collectTmdb() throws Exception {
        launcher.startTmdb(properties.getTmdb().getMaxPages());
    }

    @Scheduled(
            cron = "${everyone.collection.schedule.sportsdb.cron:-}",
            zone = "${everyone.collection.schedule.zone:Asia/Seoul}",
            scheduler = "contentCollectionTaskScheduler"
    )
    public void collectSportsDb() throws Exception {
        launcher.startSportsDb(
                properties.getSportsdb().getLeagueId(),
                properties.getSportsdb().getSeason()
        );
    }
}