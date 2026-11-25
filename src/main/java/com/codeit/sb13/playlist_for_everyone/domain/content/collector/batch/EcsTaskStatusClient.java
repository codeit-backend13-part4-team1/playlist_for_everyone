package com.codeit.sb13.playlist_for_everyone.domain.content.collector.batch;

import org.springframework.util.StringUtils;
import software.amazon.awssdk.services.ecs.EcsClient;
import software.amazon.awssdk.services.ecs.model.DescribeTasksRequest;

public class EcsTaskStatusClient {

    private final EcsClient ecsClient;

    public EcsTaskStatusClient(EcsClient ecsClient) {
        this.ecsClient = ecsClient;
    }

    /** 대상 태스크의 종료가 확인된 경우에만 true 반환 */
    public boolean isStopped(CollectionExecutionOwner owner) {
        if (owner == null
                || !StringUtils.hasText(owner.clusterArn())
                || !StringUtils.hasText(owner.taskArn())) {
            return false;
        }

        var request = DescribeTasksRequest.builder()
                .cluster(owner.clusterArn())
                .tasks(owner.taskArn())
                .build();

        var response = ecsClient.describeTasks(request);

        if (response == null || !response.failures().isEmpty()) {
            return false;
        }

        return response.tasks().stream()
                .anyMatch(task ->
                        owner.taskArn().equals(task.taskArn())
                                && "STOPPED".equals(task.lastStatus())
                );
    }
}