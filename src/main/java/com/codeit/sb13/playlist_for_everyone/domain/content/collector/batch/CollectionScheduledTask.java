package com.codeit.sb13.playlist_for_everyone.domain.content.collector.batch;

import org.springframework.scheduling.SchedulingAwareRunnable;
import org.springframework.scheduling.support.CronTrigger;

import java.time.Instant;
import java.time.ZoneId;

public class CollectionScheduledTask extends CronTrigger implements SchedulingAwareRunnable {

    private final Action action;

    public CollectionScheduledTask(String cron, ZoneId zone, Action action) {
        super(cron, zone);
        this.action = action;
    }

    @Override
    public String getQualifier() {
        return "contentCollectionTaskScheduler";
    }

    @Override
    public void run() {
    }

    @FunctionalInterface
    public interface Action {
        void run(Instant scheduledAt) throws Exception;
    }
}