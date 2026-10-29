package com.codeit.sb13.playlist_for_everyone.global.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.scheduling.TaskScheduler;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("스케줄러 설정")
class SchedulerConfigTest {

    @Test
    @DisplayName("SSE 전용 스케줄러는 실제 작업을 전용 스레드에서 실행한다.")
    void sseHeartbeatSchedulerExecutesTaskOnDedicatedThread() throws ExecutionException, InterruptedException, TimeoutException {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext(SchedulerConfig.class)){

            TaskScheduler scheduler = context.getBean("sseHeartbeatTaskScheduler", TaskScheduler.class);

            CompletableFuture<String> executedThreadName = new CompletableFuture<>();

            scheduler.schedule(()-> executedThreadName.complete(Thread.currentThread().getName()),
                    Instant.now());

            assertThat(executedThreadName.get(5, TimeUnit.SECONDS))
                    .startsWith("sse-heartbeat-");

        }
    }

    @Test
    @DisplayName("컨텍스트가 종료되면 SSE 전용 스케줄러는 새 작업을 거부한다.")
    void  sseHeartbeatSchedulerRejectsTasksAfterContextClosed(){
        TaskScheduler scheduler;
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext(SchedulerConfig.class)){
            scheduler= context.getBean("sseHeartbeatTaskScheduler", TaskScheduler.class);
        }
        assertThatThrownBy(()-> scheduler.schedule(() ->{}, Instant.now()))
                .isInstanceOf(TaskRejectedException.class);
    }

    @Test
    @DisplayName("컨텍스트가 종료되면 SSE 전용 스케줄러에 등록된 반복 작업이 취소된다.")
    void sseHeartbeatSchedulerCancelsPeriodicTaskWhenContextClosed(){
        ScheduledFuture<?> periodicTask;

        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext(SchedulerConfig.class)){
            TaskScheduler scheduler = context.getBean("sseHeartbeatTaskScheduler", TaskScheduler.class);

            periodicTask = scheduler.scheduleWithFixedDelay(()->{},
                    Instant.now().plusSeconds(60), Duration.ofSeconds(15));

            assertThat(periodicTask.isCancelled()).isFalse();
        }

        assertThat(periodicTask.isCancelled()).isTrue();
    }

}