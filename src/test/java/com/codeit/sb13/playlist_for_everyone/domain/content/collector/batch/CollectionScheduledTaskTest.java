package com.codeit.sb13.playlist_for_everyone.domain.content.collector.batch;

import org.junit.jupiter.api.Test;
import org.springframework.scheduling.support.SimpleTriggerContext;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class CollectionScheduledTaskTest {

    @Test
    void 계산된_예약_시각을_수집_작업에_전달한다() throws Exception {
        var action = mock(CollectionScheduledTask.Action.class);
        var task = new CollectionScheduledTask(
                "0 0 0 * * *",
                ZoneId.of("Asia/Seoul"),
                action
        );

        var clock = Clock.fixed(
                Instant.parse("2026-11-26T14:59:50Z"),
                ZoneId.of("UTC")
        );
        var context = new SimpleTriggerContext(clock);
        var scheduledAt = Instant.parse("2026-11-26T15:00:00Z");

        assertThat(task.nextExecution(context)).isEqualTo(scheduledAt);

        task.run();

        verify(action).run(scheduledAt);
    }
}