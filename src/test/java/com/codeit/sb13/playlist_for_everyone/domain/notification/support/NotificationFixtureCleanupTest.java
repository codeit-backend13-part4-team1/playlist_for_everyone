package com.codeit.sb13.playlist_for_everyone.domain.notification.support;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.opentest4j.TestAbortedException;
import org.springframework.core.task.TaskDecorator;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeoutException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

@DisplayName("알림 통합 테스트의 작업 대기와 픽스처 정리")
class NotificationFixtureCleanupTest {

    private final NotificationFixtureCleanup cleanup = new NotificationFixtureCleanup();

    @Test
    @DisplayName("모든 작업이 정상 완료되면 리소스와 픽스처를 순서대로 정리한다.")
    void cleansUpAfterAllTasksComplete() {
        ObservedTasks tasks = observeTasks();
        List<String> steps = new ArrayList<>();
        tasks.decorator().decorate(() -> steps.add("알림 작업 완료")).run();

        cleanup.cleanUp(
                List.of(tasks.tracker()),
                List.of(() -> steps.add("노드 종료")),
                () -> steps.add("픽스처 삭제")
        );

        assertThat(steps).containsExactly("알림 작업 완료", "노드 종료", "픽스처 삭제");
        tasks.verifyDecoratorRestored();
        assertThatCode(() -> cleanup.beforeEach(null)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("작업 실패 후에도 픽스처를 정리하고 정리 오류는 원래 작업 실패에 덧붙인다.")
    void cleansUpAfterTaskFailureAndPreservesItsCause() {
        ObservedTasks tasks = observeTasks();
        IllegalStateException taskFailure = new IllegalStateException("알림 작업 실패");
        IllegalStateException cleanupFailure = new IllegalStateException("픽스처 정리 실패");
        Runnable failedTask = tasks.decorator().decorate(() -> { throw taskFailure; });
        assertThat(catchThrowable(failedTask::run)).isSameAs(taskFailure);

        Throwable reported = catchThrowable(() -> cleanup.cleanUp(tasks.tracker(), () -> {
            throw cleanupFailure;
        }));

        assertThat(reported).isInstanceOf(AssertionError.class).hasCause(taskFailure);
        assertThat(reported.getSuppressed()).singleElement()
                .satisfies(failure -> assertThat(failure.getSuppressed()).containsExactly(cleanupFailure));
        tasks.verifyDecoratorRestored();
        assertThatCode(() -> cleanup.beforeEach(null)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("한 노드라도 작업 종료를 확인하지 못하면 픽스처를 보존하고 후속 테스트를 격리한다.")
    void keepsFixturesWhenAnotherNodeHasAnUnfinishedTask() {
        ObservedTasks firstNode = observeTasks();
        ObservedTasks secondNode = observeTasks();
        IllegalStateException taskFailure = new IllegalStateException("첫 노드의 알림 작업 실패");
        Runnable failedTask = firstNode.decorator().decorate(() -> { throw taskFailure; });
        assertThat(catchThrowable(failedTask::run)).isSameAs(taskFailure);
        // 제출만 완료한 두 번째 노드의 작업은 아직 큐에 있다.
        secondNode.decorator().decorate(() -> {});
        List<String> steps = new ArrayList<>();

        Throwable reported = catchThrowable(() -> cleanup.cleanUp(
                List.of(firstNode.tracker(), secondNode.tracker()),
                List.of(() -> steps.add("노드 종료")),
                () -> steps.add("픽스처 삭제")
        ));

        assertThat(steps).containsExactly("노드 종료");
        assertThat(reported).hasCause(taskFailure);
        assertThat(reported.getSuppressed()).singleElement()
                .isInstanceOfSatisfying(NotificationTaskTracker.CompletionUnconfirmed.class,
                        failure -> assertThat(failure).hasCauseInstanceOf(TimeoutException.class));
        firstNode.verifyDecoratorRestored();
        secondNode.verifyDecoratorRestored();
        assertThat(catchThrowable(() -> cleanup.beforeEach(null))).isInstanceOf(TestAbortedException.class);
    }

    @Test
    @DisplayName("작업 대기가 인터럽트되면 플래그를 복원하고 픽스처 정리를 보류한다.")
    void defersCleanupWhenInterrupted() {
        ObservedTasks tasks = observeTasks();
        tasks.decorator().decorate(() -> {});
        List<String> steps = new ArrayList<>();

        Thread.currentThread().interrupt();
        try {
            Throwable reported = catchThrowable(() -> cleanup.cleanUp(
                    tasks.tracker(), () -> steps.add("픽스처 삭제")
            ));

            assertThat(reported).isInstanceOf(NotificationTaskTracker.CompletionUnconfirmed.class)
                    .hasCauseInstanceOf(InterruptedException.class);
            assertThat(Thread.currentThread().isInterrupted()).isTrue();
        } finally {
            Thread.interrupted();
        }

        assertThat(steps).isEmpty();
        tasks.verifyDecoratorRestored();
        assertThat(catchThrowable(() -> cleanup.beforeEach(null))).isInstanceOf(TestAbortedException.class);
    }

    private ObservedTasks observeTasks() {
        ThreadPoolTaskExecutor executor = mock(ThreadPoolTaskExecutor.class);
        TaskDecorator original = task -> task;
        NotificationTaskTracker tracker = new NotificationTaskTracker(executor, original, Duration.ZERO);
        ArgumentCaptor<TaskDecorator> captor = ArgumentCaptor.forClass(TaskDecorator.class);
        verify(executor).setTaskDecorator(captor.capture());
        return new ObservedTasks(tracker, captor.getValue(), executor, original);
    }

    private record ObservedTasks(
            NotificationTaskTracker tracker, TaskDecorator decorator,
            ThreadPoolTaskExecutor executor, TaskDecorator original
    ) {
        private void verifyDecoratorRestored() {
            verify(executor).setTaskDecorator(original);
        }
    }
}
