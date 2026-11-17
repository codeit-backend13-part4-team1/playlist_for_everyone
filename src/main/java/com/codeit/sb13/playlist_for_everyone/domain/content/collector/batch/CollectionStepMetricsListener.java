package com.codeit.sb13.playlist_for_everyone.domain.content.collector.batch;

import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.batch.core.ExitStatus;
import org.springframework.batch.core.listener.StepExecutionListener;
import org.springframework.batch.core.step.StepExecution;

public class CollectionStepMetricsListener implements StepExecutionListener {

    private final MeterRegistry meterRegistry;

    public CollectionStepMetricsListener(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    @Override
    public ExitStatus afterStep(StepExecution stepExecution) {
        String jobName = stepExecution.getJobExecution()
                .getJobInstance()
                .getJobName();

        String stepName = stepExecution.getStepName();

        meterRegistry.counter(
                "content.collection.items.read",
                "job", jobName,
                "step", stepName
        ).increment(stepExecution.getReadCount());

        return null;
    }
}