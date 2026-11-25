package com.codeit.sb13.playlist_for_everyone.domain.content.collector.batch.listener;

import com.codeit.sb13.playlist_for_everyone.domain.content.collector.batch.CollectionExecutionOwner;
import com.codeit.sb13.playlist_for_everyone.domain.content.collector.batch.CollectionExecutionRecoveryPolicy;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.listener.JobExecutionListener;
import org.springframework.batch.core.repository.JobRepository;

import java.util.Optional;
import java.util.function.Supplier;

public class CollectionExecutionOwnerListener implements JobExecutionListener {

    public static final String CLUSTER_ARN_KEY = "collection.owner.clusterArn";
    public static final String TASK_ARN_KEY = "collection.owner.taskArn";

    private final Supplier<Optional<CollectionExecutionOwner>> ownerSupplier;
    private final JobRepository jobRepository;

    public CollectionExecutionOwnerListener(
            Supplier<Optional<CollectionExecutionOwner>> ownerSupplier,
            JobRepository jobRepository
    ) {
        this.ownerSupplier = ownerSupplier;
        this.jobRepository = jobRepository;
    }

    /** 배치 시작 전에 현재 실행 태스크 조회 및 기록 */
    @Override
    public void beforeJob(JobExecution jobExecution) {
        var owner = ownerSupplier.get();
        var context = jobExecution.getExecutionContext();

        context.remove(CollectionExecutionRecoveryPolicy.RESTART_PENDING_KEY);

        context.remove(CLUSTER_ARN_KEY);
        context.remove(TASK_ARN_KEY);

        owner.ifPresent(current -> {
            context.putString(CLUSTER_ARN_KEY, current.clusterArn());
            context.putString(TASK_ARN_KEY, current.taskArn());
        });

        jobRepository.updateExecutionContext(jobExecution);
    }
}