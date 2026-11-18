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

/**
 * 실제 실행기 빈을 만들어 전용 스레드 실행과 컨텍스트 종료 후 자원 정리를 확인한다.
 * 스케줄 등록 Mock 테스트와 달리 실제 스레드 및 ScheduledFuture의 상태를 사용한다.
 */
@DisplayName("스케줄러 설정")
class SchedulerConfigTest {

    @Test
    @DisplayName("SSE 전용 스케줄러는 실제 작업을 전용 스레드에서 실행한다.")
    void sseHeartbeatSchedulerExecutesTaskOnDedicatedThread() throws ExecutionException, InterruptedException, TimeoutException {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext(SchedulerConfig.class)){

            TaskScheduler scheduler = context.getBean("sseHeartbeatTaskScheduler", TaskScheduler.class);

            // 작업 스레드에서 실행 정보를 돌려받아 sleep 없이 완료를 기다린다. 5초 제한으로 무한 대기를 막는다.
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
        // try-with-resources가 컨텍스트를 닫은 뒤의 실행기에 새 작업을 제출한다.
        assertThatThrownBy(()-> scheduler.schedule(() ->{}, Instant.now()))
                .isInstanceOf(TaskRejectedException.class);
    }

    @Test
    @DisplayName("컨텍스트가 종료되면 SSE 전용 스케줄러에 등록된 반복 작업이 취소된다.")
    void sseHeartbeatSchedulerCancelsPeriodicTaskWhenContextClosed(){
        ScheduledFuture<?> periodicTask;

        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext(SchedulerConfig.class)){
            TaskScheduler scheduler = context.getBean("sseHeartbeatTaskScheduler", TaskScheduler.class);

            // 첫 실행을 60초 뒤로 예약해 실제 반복 실행을 기다리지 않고, 종료에 따른 취소만 관찰한다.
            periodicTask = scheduler.scheduleWithFixedDelay(()->{},
                    Instant.now().plusSeconds(60), Duration.ofSeconds(15));

            assertThat(periodicTask.isCancelled()).isFalse();
        }

        // 실행기 빈의 종료 처리가 등록된 반복 작업도 취소했는지 확인한다.
        assertThat(periodicTask.isCancelled()).isTrue();
    }

}
