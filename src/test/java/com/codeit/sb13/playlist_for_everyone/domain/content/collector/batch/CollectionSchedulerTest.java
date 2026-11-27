package com.codeit.sb13.playlist_for_everyone.domain.content.collector.batch;

import com.codeit.sb13.playlist_for_everyone.domain.content.collector.config.CollectionScheduleProperties;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.config.ScheduledTaskRegistrar;
import org.springframework.scheduling.support.SimpleTriggerContext;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

class CollectionSchedulerTest {

    @Test
    void 설정한_페이지_수와_예약_시각으로_TMDB_수집을_요청한다() throws Exception {
        var launcher = mock(CollectionJobLauncher.class);
        var properties = new CollectionScheduleProperties();
        properties.getTmdb().setCron("0 0 0 * * *");
        properties.getTmdb().setMaxPages(7);

        var scheduler = new CollectionScheduler(launcher, properties);
        var scheduledAt = Instant.parse("2026-11-26T15:00:00Z");

        runScheduledTask(scheduler, scheduledAt);

        verify(launcher).startScheduledTmdb(7L, scheduledAt);
        verifyNoMoreInteractions(launcher);
    }

    @Test
    void 설정한_리그와_시즌과_예약_시각으로_스포츠_수집을_요청한다() throws Exception {
        var launcher = mock(CollectionJobLauncher.class);
        var properties = new CollectionScheduleProperties();
        properties.getSportsdb().setCron("0 30 0 * * *");
        properties.getSportsdb().setLeagueId("4328");
        properties.getSportsdb().setSeason("2014-2015");

        var scheduler = new CollectionScheduler(launcher, properties);
        var scheduledAt = Instant.parse("2026-11-26T15:30:00Z");

        runScheduledTask(scheduler, scheduledAt);

        verify(launcher).startScheduledSportsDb(
                "4328", "2014-2015", scheduledAt
        );
        verifyNoMoreInteractions(launcher);
    }

    private void runScheduledTask(
            CollectionScheduler scheduler,
            Instant scheduledAt
    ) {
        var registrar = new ScheduledTaskRegistrar();
        scheduler.configureTasks(registrar);

        assertThat(registrar.getTriggerTaskList()).hasSize(1);

        var task = registrar.getTriggerTaskList().get(0);
        var clock = Clock.fixed(
                Instant.parse("2026-11-26T14:59:50Z"),
                ZoneId.of("UTC")
        );
        var context = new SimpleTriggerContext(clock);

        assertThat(task.getTrigger().nextExecution(context))
                .isEqualTo(scheduledAt);

        task.getRunnable().run();
    }
}