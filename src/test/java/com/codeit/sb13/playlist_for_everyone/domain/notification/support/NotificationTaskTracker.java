package com.codeit.sb13.playlist_for_everyone.domain.notification.support;

import org.springframework.core.task.TaskDecorator;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * 테스트 컨텍스트의 실제 알림 실행기에 제출된 작업의 완료·실패를 관찰한다.
 * 작업 제출을 유발한 호출이나 응답을 확인한 뒤 대기해야 하며, 이후 제출될 작업까지 기다리지는 않는다.
 * 별도의 실행기로 대체하지 않고, 테스트 종료 시 관찰용 데코레이터만 해제한다.
 */
public final class NotificationTaskTracker implements AutoCloseable {

    private final ThreadPoolTaskExecutor executor;
    private final TaskDecorator contextDecorator;
    private final Duration completionTimeout;
    private final List<CompletableFuture<Void>> tasks = new CopyOnWriteArrayList<>();

    public NotificationTaskTracker(ThreadPoolTaskExecutor executor, TaskDecorator contextDecorator) {
        this(executor, contextDecorator, Duration.ofSeconds(5));
    }

    NotificationTaskTracker(
            ThreadPoolTaskExecutor executor, TaskDecorator contextDecorator, Duration completionTimeout
    ) {
        this.executor = executor;
        this.contextDecorator = contextDecorator;
        this.completionTimeout = completionTimeout;
        executor.setTaskDecorator(task -> {
            Runnable decoratedTask = contextDecorator.decorate(task);
            CompletableFuture<Void> completion = new CompletableFuture<>();
            tasks.add(completion);
            return () -> {
                try {
                    decoratedTask.run();
                    completion.complete(null);
                } catch (RuntimeException | Error failure) {
                    completion.completeExceptionally(failure);
                    throw failure;
                }
            };
        });
    }

    public boolean hasSubmittedTasks() {
        return !tasks.isEmpty();
    }

    public int submittedTaskCount() {
        return tasks.size();
    }

    public void awaitCompletion() {
        try {
            CompletableFuture.allOf(tasks.toArray(CompletableFuture[]::new))
                    .get(completionTimeout.toNanos(), TimeUnit.NANOSECONDS);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new CompletionUnconfirmed("알림 작업 완료 대기 중 인터럽트가 발생했습니다.", exception);
        } catch (TimeoutException exception) {
            throw new CompletionUnconfirmed("제한 시간 내에 알림 작업 완료를 확인하지 못했습니다.", exception);
        } catch (ExecutionException exception) {
            // allOf는 실패한 작업을 포함해 추적한 작업이 모두 끝난 뒤 완료된다.
            throw new AssertionError("종료된 알림 작업에서 예외가 발생했습니다.", exception.getCause());
        }
    }

    @Override
    public void close() {
        try {
            awaitCompletion();
        } finally {
            executor.setTaskDecorator(contextDecorator);
        }
    }

    static final class CompletionUnconfirmed extends AssertionError {

        private CompletionUnconfirmed(String message, Exception cause) {
            super(message, cause);
        }
    }
}
