package com.codeit.sb13.playlist_for_everyone.domain.content.collector.batch;

import com.codeit.sb13.playlist_for_everyone.domain.content.collector.config.CollectionScheduleProperties;
import org.springframework.scheduling.annotation.SchedulingConfigurer;
import org.springframework.scheduling.config.ScheduledTaskRegistrar;

import java.time.ZoneId;

public class CollectionScheduler implements SchedulingConfigurer {

    private final CollectionJobLauncher launcher;
    private final CollectionScheduleProperties properties;

    public CollectionScheduler(
            CollectionJobLauncher launcher,
            CollectionScheduleProperties properties
    ) {
        this.launcher = launcher;
        this.properties = properties;
    }

    @Override
    public void configureTasks(ScheduledTaskRegistrar taskRegistrar) {
        registerTask(taskRegistrar, properties.getTmdb().getCron(), scheduledAt -> {
            launcher.startScheduledTmdb(
                    properties.getTmdb().getMaxPages(),
                    scheduledAt
            );
        });

        registerTask(taskRegistrar, properties.getSportsdb().getCron(), scheduledAt -> {
            launcher.startScheduledSportsDb(
                    properties.getSportsdb().getLeagueId(),
                    properties.getSportsdb().getSeason(),
                    scheduledAt
            );
        });
    }

    private void registerTask(
            ScheduledTaskRegistrar taskRegistrar,
            String cron,
            CollectionScheduledTask.Action action
    ) {
        if (ScheduledTaskRegistrar.CRON_DISABLED.equals(cron)) {
            return;
        }

        var task = new CollectionScheduledTask(cron, ZoneId.of(properties.getZone()), action);

        taskRegistrar.addTriggerTask(task, task);
    }
}