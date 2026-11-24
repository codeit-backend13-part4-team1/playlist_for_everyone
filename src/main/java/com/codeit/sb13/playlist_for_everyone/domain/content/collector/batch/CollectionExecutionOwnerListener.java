package com.codeit.sb13.playlist_for_everyone.domain.content.collector.batch;

import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.listener.JobExecutionListener;
import org.springframework.batch.core.repository.JobRepository;

import java.util.Optional;

public class CollectionExecutionOwnerListener implements JobExecutionListener {

    public static final String CLUSTER_ARN_KEY = "collection.owner.clusterArn";
    public static final String TASK_ARN_KEY = "collection.owner.taskArn";

    private final Optional<CollectionExecutionOwner> owner;
    private final JobRepository jobRepository;

    public CollectionExecutionOwnerListener(
            Optional<CollectionExecutionOwner> owner,
            JobRepository jobRepository
    ) {
        this.owner = owner;
        this.jobRepository = jobRepository;
    }

    /** 배치 시작 전에 현재 실행 태스크 기록 */
    @Override
    public void beforeJob(JobExecution jobExecution) {
        var context = jobExecution.getExecutionContext();

        context.remove(CLUSTER_ARN_KEY);
        context.remove(TASK_ARN_KEY);

        owner.ifPresent(current -> {
            context.putString(CLUSTER_ARN_KEY, current.clusterArn());
            context.putString(TASK_ARN_KEY, current.taskArn());
        });

        jobRepository.updateExecutionContext(jobExecution);
    }
}