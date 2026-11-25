package com.codeit.sb13.playlist_for_everyone.domain.content.collector.batch;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import software.amazon.awssdk.services.ecs.EcsClient;
import software.amazon.awssdk.services.ecs.model.DescribeTasksRequest;
import software.amazon.awssdk.services.ecs.model.DescribeTasksResponse;
import software.amazon.awssdk.services.ecs.model.EcsException;
import software.amazon.awssdk.services.ecs.model.Task;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class EcsTaskStatusClientTest {

    private final EcsClient ecsClient = mock(EcsClient.class);
    private final EcsTaskStatusClient client = new EcsTaskStatusClient(ecsClient);

    private final CollectionExecutionOwner owner = new CollectionExecutionOwner(
            "arn:aws:ecs:ap-northeast-2:123456789012:cluster/test",
            "arn:aws:ecs:ap-northeast-2:123456789012:task/test/previous"
    );

    @ParameterizedTest
    @CsvSource({
            "STOPPED, true",
            "RUNNING, false",
            "STOPPING, false",
            "DEPROVISIONING, false",
            "PENDING, false"
    })
    void 실제_상태가_STOPPED일_때만_종료로_판정한다(
            String status,
            boolean expected
    ) {
        var task = Task.builder()
                .taskArn(owner.taskArn())
                .desiredStatus("STOPPED")
                .lastStatus(status)
                .build();

        when(ecsClient.describeTasks(any(DescribeTasksRequest.class)))
                .thenReturn(DescribeTasksResponse.builder()
                        .tasks(task)
                        .build());

        assertThat(client.isStopped(owner)).isEqualTo(expected);

        var captor = ArgumentCaptor.forClass(DescribeTasksRequest.class);
        verify(ecsClient).describeTasks(captor.capture());

        assertThat(captor.getValue().cluster()).isEqualTo(owner.clusterArn());
        assertThat(captor.getValue().tasks()).containsExactly(owner.taskArn());
    }

    @Test
    void 태스크_정보가_없으면_종료로_판정하지_않는다() {
        when(ecsClient.describeTasks(any(DescribeTasksRequest.class)))
                .thenReturn(DescribeTasksResponse.builder().build());

        assertThat(client.isStopped(owner)).isFalse();
    }

    @Test
    void 다른_태스크가_종료됐어도_대상_태스크의_종료로_판정하지_않는다() {
        var task = Task.builder()
                .taskArn("different-task")
                .lastStatus("STOPPED")
                .build();

        when(ecsClient.describeTasks(any(DescribeTasksRequest.class)))
                .thenReturn(DescribeTasksResponse.builder()
                        .tasks(task)
                        .build());

        assertThat(client.isStopped(owner)).isFalse();
    }

    @Test
    void 조회_권한_오류는_호출자에게_전달한다() {
        var failure = EcsException.builder()
                .statusCode(403)
                .message("Access denied")
                .build();

        when(ecsClient.describeTasks(any(DescribeTasksRequest.class)))
                .thenThrow(failure);

        assertThatThrownBy(() -> client.isStopped(owner))
                .isSameAs(failure);
    }
}