package com.codeit.sb13.playlist_for_everyone.domain.content.collector.batch.listener;

import com.codeit.sb13.playlist_for_everyone.domain.content.collector.batch.CollectionExecutionOwner;
import org.junit.jupiter.api.Test;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.infrastructure.item.ExecutionContext;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CollectionExecutionOwnerListenerTest {

    @Test
    void 배치_시작_전에_현재_실행_태스크를_기록한다() {
        var repository = mock(JobRepository.class);
        var execution = mock(JobExecution.class);
        var context = new ExecutionContext();

        when(execution.getExecutionContext()).thenReturn(context);

        var owner = new CollectionExecutionOwner(
                "arn:aws:ecs:ap-northeast-2:123456789012:cluster/test",
                "arn:aws:ecs:ap-northeast-2:123456789012:task/test/current"
        );

        var listener = new CollectionExecutionOwnerListener(
                Optional.of(owner),
                repository
        );

        listener.beforeJob(execution);

        assertThat(context.getString("collection.owner.clusterArn"))
                .isEqualTo(owner.clusterArn());
        assertThat(context.getString("collection.owner.taskArn"))
                .isEqualTo(owner.taskArn());

        verify(repository).updateExecutionContext(execution);
    }

    @Test
    void 현재_실행_주체를_모르면_이전_태스크_정보를_제거한다() {
        var repository = mock(JobRepository.class);
        var execution = mock(JobExecution.class);
        var context = new ExecutionContext();

        context.putString("collection.owner.clusterArn", "previous-cluster");
        context.putString("collection.owner.taskArn", "previous-task");

        when(execution.getExecutionContext()).thenReturn(context);

        var listener = new CollectionExecutionOwnerListener(
                Optional.empty(),
                repository
        );

        listener.beforeJob(execution);

        assertThat(context.containsKey("collection.owner.clusterArn")).isFalse();
        assertThat(context.containsKey("collection.owner.taskArn")).isFalse();

        verify(repository).updateExecutionContext(execution);
    }
}