package com.codeit.sb13.playlist_for_everyone.domain.content.collector.batch.listener;

import com.codeit.sb13.playlist_for_everyone.domain.content.collector.batch.CollectionInsertCounter;
import com.codeit.sb13.playlist_for_everyone.domain.content.event.ContentCollectionFinishedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.listener.JobExecutionListener;
import org.springframework.context.ApplicationEventPublisher;

@RequiredArgsConstructor
public class CollectionCacheEvictJobListener implements JobExecutionListener {

    private final CollectionInsertCounter counter;
    private final ApplicationEventPublisher eventPublisher;

    @Override
    public void afterJob(JobExecution jobExecution) {
        var event = new ContentCollectionFinishedEvent(
                counter.getCount(),
                counter.getUpdatedCount(),
                counter.getChangedContentIds()
        );

        if (event.changed()) {
            eventPublisher.publishEvent(event);
        }
    }
}