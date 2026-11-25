package com.codeit.sb13.playlist_for_everyone.domain.content.collector.batch;

import com.codeit.sb13.playlist_for_everyone.domain.content.collector.batch.listener.CollectionExecutionOwnerListener;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.util.StringUtils;

public class CollectionExecutionRecoveryPolicy {

    private final EcsTaskStatusClient statusClient;

    public static final String RESTART_PENDING_KEY = "collection.recovery.restartPending";

    public CollectionExecutionRecoveryPolicy(EcsTaskStatusClient statusClient) {
        this.statusClient = statusClient;
    }

    /** 이전 실행 태스크의 종료가 확인된 경우에만 복구 허용 */
    public boolean canRecover(JobExecution execution) {
        var context = execution.getExecutionContext();

        String clusterArn = context.getString(
                CollectionExecutionOwnerListener.CLUSTER_ARN_KEY,
                null
        );
        String taskArn = context.getString(
                CollectionExecutionOwnerListener.TASK_ARN_KEY,
                null
        );

        if (!StringUtils.hasText(clusterArn)
                || !StringUtils.hasText(taskArn)) {
            return false;
        }

        return statusClient.isStopped(new CollectionExecutionOwner(
                clusterArn,
                taskArn
        ));
    }
}