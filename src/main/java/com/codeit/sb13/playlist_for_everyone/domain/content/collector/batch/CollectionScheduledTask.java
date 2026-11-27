package com.codeit.sb13.playlist_for_everyone.domain.content.collector.batch;

import org.springframework.scheduling.SchedulingAwareRunnable;
import org.springframework.scheduling.TriggerContext;
import org.springframework.scheduling.support.CronTrigger;

import java.time.Instant;
import java.time.ZoneId;
import java.util.Objects;

public class CollectionScheduledTask extends CronTrigger implements SchedulingAwareRunnable {

    private final Action action;
    private volatile Instant scheduledAt;

    public CollectionScheduledTask(String cron, ZoneId zone, Action action) {
        super(cron, zone);
        this.action = Objects.requireNonNull(action, "수집 작업은 필수입니다.");
    }

    @Override
    public String getQualifier() {
        return "contentCollectionTaskScheduler";
    }

    @Override
    public Instant nextExecution(TriggerContext triggerContext) {
        Instant next = super.nextExecution(triggerContext);
        scheduledAt = next;
        return next;
    }

    @Override
    public void run() {
        Instant executionTime = Objects.requireNonNull(scheduledAt, "예약 시각이 계산되지 않았습니다.");

        try {
            action.run(executionTime);
        } catch (RuntimeException exception) {
            throw exception;
        } catch (Exception exception) {
            if (exception instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }

            throw new IllegalStateException("콘텐츠 정기 수집 실행에 실패했습니다.", exception);
        }
    }

    @FunctionalInterface
    public interface Action {
        void run(Instant scheduledAt) throws Exception;
    }
}