package com.codeit.sb13.playlist_for_everyone.domain.content.collector.batch;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.job.JobInstance;
import org.springframework.batch.core.step.StepExecution;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CollectionStepMetricsListenerTest {

    @Test
    void Step이_끝나면_읽은_건수를_기록한다() {
        var registry = new SimpleMeterRegistry();
        try {
            var listener = new CollectionStepMetricsListener(registry);
            var stepExecution = mock(StepExecution.class);
            var jobExecution = mock(JobExecution.class);
            var jobInstance = mock(JobInstance.class);

            when(stepExecution.getJobExecution()).thenReturn(jobExecution);
            when(jobExecution.getJobInstance()).thenReturn(jobInstance);
            when(jobInstance.getJobName()).thenReturn("tmdbCollectionJob");
            when(stepExecution.getStepName()).thenReturn("tmdbMovieStep");
            when(stepExecution.getReadCount()).thenReturn(3L);

            listener.afterStep(stepExecution);

            var counter = registry.find("content.collection.items.read")
                    .tags(
                            "job", "tmdbCollectionJob",
                            "step", "tmdbMovieStep"
                    )
                    .counter();

            assertThat(counter).isNotNull();
            assertThat(counter.count()).isEqualTo(3.0);
            assertThat(counter).isNotNull();
            assertThat(counter.count()).isEqualTo(3.0);
        } finally {
            registry.close();
        }
    }
}