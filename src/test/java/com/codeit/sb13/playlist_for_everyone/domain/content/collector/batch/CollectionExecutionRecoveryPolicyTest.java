package com.codeit.sb13.playlist_for_everyone.domain.content.collector.batch;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.infrastructure.item.ExecutionContext;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class CollectionExecutionRecoveryPolicyTest {

    private final EcsTaskStatusClient statusClient =
            mock(EcsTaskStatusClient.class);
    private final JobExecution execution = mock(JobExecution.class);
    private final ExecutionContext context = new ExecutionContext();

    private final CollectionExecutionRecoveryPolicy policy =
            new CollectionExecutionRecoveryPolicy(statusClient);

    private final CollectionExecutionOwner owner = new CollectionExecutionOwner(
            "arn:aws:ecs:ap-northeast-2:123456789012:cluster/test",
            "arn:aws:ecs:ap-northeast-2:123456789012:task/test/previous"
    );

    @BeforeEach
    void setUp() {
        when(execution.getExecutionContext()).thenReturn(context);
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void 실행_태스크의_종료가_확인된_경우에만_복구를_허용한다(
            boolean stopped
    ) {
        context.putString("collection.owner.clusterArn", owner.clusterArn());
        context.putString("collection.owner.taskArn", owner.taskArn());

        when(statusClient.isStopped(owner)).thenReturn(stopped);

        assertThat(policy.canRecover(execution)).isEqualTo(stopped);

        verify(statusClient).isStopped(owner);
    }

    @ParameterizedTest
    @CsvSource(
            value = {
                    "NULL, test-task",
                    "test-cluster, NULL",
                    "NULL, NULL",
                    "'', test-task",
                    "test-cluster, ''"
            },
            nullValues = "NULL"
    )
    void 실행_주체_정보가_없으면_조회하거나_복구하지_않는다(
            String clusterArn,
            String taskArn
    ) {
        context.putString("collection.owner.clusterArn", clusterArn);
        context.putString("collection.owner.taskArn", taskArn);

        assertThat(policy.canRecover(execution)).isFalse();

        verifyNoInteractions(statusClient);
    }

    @Test
    void 태스크_상태_조회에_실패하면_오류를_전달한다() {
        context.putString("collection.owner.clusterArn", owner.clusterArn());
        context.putString("collection.owner.taskArn", owner.taskArn());

        var failure = new IllegalStateException("ECS 태스크 조회 실패");

        when(statusClient.isStopped(owner)).thenThrow(failure);

        assertThatThrownBy(() -> policy.canRecover(execution))
                .isSameAs(failure);
    }
}