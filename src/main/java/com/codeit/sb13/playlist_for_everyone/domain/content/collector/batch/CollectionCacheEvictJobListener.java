package com.codeit.sb13.playlist_for_everyone.domain.content.collector.batch;

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
        throw new UnsupportedOperationException("배치 종료 이벤트 구현 전");
    }
}