package com.codeit.sb13.playlist_for_everyone.global.config;

import com.codeit.sb13.playlist_for_everyone.global.sse.config.SseRetryProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.scheduling.TaskScheduler;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("SSE 재시도 설정 빈 등록 및 바인딩")
class SseRetryConfigTest {
    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(SseRetryConfig.class);

    @Test
    @DisplayName("정상 재시도 설정이면 설정 빈이 등록되고 간격과 최대 횟수가 바인딩된다.")
    void registerAndBindsValidRetryProperties(){
        contextRunner.withPropertyValues(
                "everyone.sse.retry.initial-delay=2s",
                "everyone.sse.retry.max-delay=10s",
                "everyone.sse.retry.max-retries=5"
        ).run(context ->{
            assertThat(context).hasNotFailed();
            assertThat(context).hasSingleBean(SseRetryProperties.class);

            SseRetryProperties properties = context.getBean(SseRetryProperties.class);
            assertThat(properties.initialDelay())
                    .isEqualTo(Duration.ofSeconds(2));
            assertThat(properties.maxDelay())
                    .isEqualTo(Duration.ofSeconds(10));
            assertThat(properties.maxRetries()).isEqualTo(5);
        });
    }

    @Test
    @DisplayName("최대 간격이 초기 간격보다 작으면 재시도 설정 검증으로 컨텍스트 시작에 실패한다.")
    void failsToStartWhenMaxDelayIsLessThanInitialDelay(){
        contextRunner.withPropertyValues(
                "everyone.sse.retry.initial-delay=2s",
                "everyone.sse.retry.max-delay=1s",
                "everyone.sse.retry.max-retries=3"
        ).run(context ->{
            assertThat(context).hasFailed();
            assertThat(context.getStartupFailure())
                    .hasRootCauseInstanceOf(IllegalArgumentException.class);
        });
    }

    @Test
    @DisplayName("최대 재시도 횟수가 0이면 설정 검증으로 컨텍스트 시작에 실패한다.")
    void failsToStartWhenMaxRetriesIsZero() {
        contextRunner.withPropertyValues(
                "everyone.sse.retry.initial-delay=1s",
                "everyone.sse.retry.max-delay=30s",
                "everyone.sse.retry.max-retries=0"
        ).run(context -> {
            assertThat(context).hasFailed();
            assertThat(context.getStartupFailure())
                    .hasRootCauseInstanceOf(IllegalArgumentException.class);
        });
    }

    @Test
    @DisplayName("재시도 설정을 적용하면 지정한 이름으로 전용 스케줄러가 등록된다.")
    void registersDedicatedRetryTaskScheduler(){
        contextRunner.withPropertyValues(
                "everyone.sse.retry.initial-delay=1s",
                "everyone.sse.retry.max-delay=30s",
                "everyone.sse.retry.max-retries=3"
        ).run(context ->{
            assertThat(context).hasNotFailed();
            assertThat(context).hasBean("sseRetryTaskScheduler");
            assertThat(context.getBean("sseRetryTaskScheduler"))
                    .isInstanceOf(TaskScheduler.class);
        });
    }

    @Test
    @DisplayName("재시도 스케줄러는 예약한 작업을 전용 스레드에서 실행한다.")
    void executesRetryTaskOnDedicatedThread(){
        contextRunner.withPropertyValues(
                "everyone.sse.retry.initial-delay=1s",
                "everyone.sse.retry.max-delay=30s",
                "everyone.sse.retry.max-retries=3"
        ).run(context ->{
            assertThat(context).hasNotFailed();
            TaskScheduler scheduler = context.getBean("sseRetryTaskScheduler", TaskScheduler.class);
            CompletableFuture<String> executedThreadName = new CompletableFuture<>();

            scheduler.schedule(()->
                    executedThreadName.complete(Thread.currentThread().getName()), Instant.now());
            assertThat(executedThreadName.get(5, TimeUnit.SECONDS))
                    .startsWith("sse-retry-");
        });
    }

    @Test
    @DisplayName("컨텍스트가 종료되면 재시도 스케절러는 새 작업을 거부한다.")
    void rejectsRetryTasksAfterContextClosed(){
        AtomicReference<TaskScheduler> schedulerReference = new AtomicReference<>();

        contextRunner.withPropertyValues(
                "everyone.sse.retry.initial-delay=1s",
                "everyone.sse.retry.max-delay=30s",
                "everyone.sse.retry.max-retries=3"
        ).run(context ->{
            assertThat(context).hasNotFailed();
            schedulerReference.set(context.getBean("sseRetryTaskScheduler", TaskScheduler.class));
        });

        TaskScheduler scheduler = schedulerReference.get();
        assertThat(scheduler).isNotNull();
        assertThatThrownBy(() -> scheduler.schedule(() -> {}, Instant.now()))
                .isInstanceOf(TaskRejectedException.class);
    }

    @Test
    @DisplayName("컨텍스트가 종료되면 실행 대기 중인 재시도 작업이 취소된다.")
    void cancelsPendingRetryTaskWhenContextClosed(){
        AtomicReference<ScheduledFuture<?>> taskReference = new AtomicReference<>();
        contextRunner.withPropertyValues(
                "everyone.sse.retry.initial-delay=1s",
                "everyone.sse.retry.max-delay=30s",
                "everyone.sse.retry.max-retries=3"
        ).run(context ->{
            assertThat(context).hasNotFailed();
            TaskScheduler scheduler = context.getBean("sseRetryTaskScheduler", TaskScheduler.class);
            ScheduledFuture<?> task = scheduler.schedule(() -> {},
                    scheduler.getClock().instant().plusSeconds(60));

            assertThat((Object) task).isNotNull();
            assertThat(task.isCancelled()).isFalse();
            taskReference.set(task);
        });

        ScheduledFuture<?> task = taskReference.get();
        assertThat((Object) task).isNotNull();
        assertThat(task.isCancelled()).isTrue();
    }
}
