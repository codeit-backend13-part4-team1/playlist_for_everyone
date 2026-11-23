package com.codeit.sb13.playlist_for_everyone.global.config;

import com.codeit.sb13.playlist_for_everyone.domain.notification.config.NotificationAsyncProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.slf4j.MDC;
import org.springframework.beans.factory.support.AbstractBeanDefinition;
import org.springframework.boot.test.context.assertj.AssertableApplicationContext;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.core.task.TaskExecutor;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("알림 전용 실행기 설정 및 자원 제한")
class NotificationAsyncConfigTest {

    private static final String EXECUTOR_BEAN_NAME = "notificationTaskExecutor";
    private static final long TIMEOUT_SECONDS = 5;

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(NotificationAsyncConfig.class)
            .withPropertyValues(
                    "everyone.notification.async.pool-size=2",
                    "everyone.notification.async.queue-capacity=3"
            );

    @Test
    @DisplayName("설정한 동시 실행 수와 대기열 크기로 알림 전용 실행기를 등록한다.")
    void registersDedicatedExecutorWithConfiguredLimits() {
        contextRunner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).hasSingleBean(NotificationAsyncProperties.class);

            NotificationAsyncProperties properties = context.getBean(NotificationAsyncProperties.class);
            assertThat(properties.poolSize()).isEqualTo(2);
            assertThat(properties.queueCapacity()).isEqualTo(3);

            TaskExecutor executor = getExecutor(context);
            assertThat(executor).isInstanceOfSatisfying(ThreadPoolTaskExecutor.class, pool -> {
                assertThat(pool.getCorePoolSize()).isEqualTo(2);
                assertThat(pool.getMaxPoolSize()).isEqualTo(2);
                assertThat(pool.getQueueCapacity()).isEqualTo(3);
            });
            assertThat(context.getBeanFactory().getBeanDefinition(EXECUTOR_BEAN_NAME))
                    .isInstanceOfSatisfying(AbstractBeanDefinition.class,
                            definition -> assertThat(definition.isDefaultCandidate()).isFalse());
        });
    }

    @ParameterizedTest(name = "동시 실행 수 {0}, 대기열 크기 {1}")
    @CsvSource({
            "0, 3",
            "-1, 3",
            "2, -1"
    })
    @DisplayName("잘못된 실행기 설정이면 컨텍스트 시작에 실패한다.")
    void rejectsInvalidExecutorSettings(int poolSize, int queueCapacity) {
        contextRunner.withPropertyValues(
                "everyone.notification.async.pool-size=" + poolSize,
                "everyone.notification.async.queue-capacity=" + queueCapacity
        ).run(context -> {
            assertThat(context).hasFailed();
            assertThat(context.getStartupFailure()).hasRootCauseInstanceOf(IllegalArgumentException.class);
        });
    }

    @Test
    @DisplayName("알림 작업을 호출 스레드와 다른 전용 스레드에서 실행한다.")
    void executesTasksOnDedicatedThread() {
        contextRunner.run(context -> {
            TaskExecutor executor = getExecutor(context);
            Thread callerThread = Thread.currentThread();
            CompletableFuture<Thread> executedThread = new CompletableFuture<>();

            executor.execute(() -> executedThread.complete(Thread.currentThread()));

            Thread workerThread = executedThread.get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
            assertThat(workerThread).isNotSameAs(callerThread);
            assertThat(workerThread.getName()).startsWith("notification-");
        });
    }

    @Test
    @DisplayName("실행·대기 상한을 넘으면 호출 스레드에서 실행하지 않고 제출을 거부한다.")
    void limitsConcurrencyAndQueueWithoutCallerExecution() {
        contextRunner.withPropertyValues(
                "everyone.notification.async.queue-capacity=1"
        ).run(context -> {
            TaskExecutor executor = getExecutor(context);
            CountDownLatch runningTasksStarted = new CountDownLatch(2);
            CountDownLatch releaseRunningTasks = new CountDownLatch(1);
            AtomicInteger firstExecutions = new AtomicInteger();
            AtomicInteger secondExecutions = new AtomicInteger();
            AtomicInteger queuedExecutions = new AtomicInteger();
            AtomicBoolean rejectedTaskExecuted = new AtomicBoolean();

            try {
                CompletableFuture<Void> firstTask = CompletableFuture.runAsync(() -> {
                    firstExecutions.incrementAndGet();
                    waitForRelease(runningTasksStarted, releaseRunningTasks);
                }, executor);
                CompletableFuture<Void> secondTask = CompletableFuture.runAsync(() -> {
                    secondExecutions.incrementAndGet();
                    waitForRelease(runningTasksStarted, releaseRunningTasks);
                }, executor);

                assertThat(runningTasksStarted.await(TIMEOUT_SECONDS, TimeUnit.SECONDS)).isTrue();

                CompletableFuture<Void> queuedTask = CompletableFuture.runAsync(
                        () -> queuedExecutions.incrementAndGet(), executor);
                assertThat(queuedTask.isDone()).isFalse();

                assertThatThrownBy(() -> executor.execute(() -> rejectedTaskExecuted.set(true)))
                        .isInstanceOf(TaskRejectedException.class);

                releaseRunningTasks.countDown();
                // 워커의 실패도 Future를 통해 테스트 스레드로 전달한다.
                CompletableFuture.allOf(firstTask, secondTask, queuedTask)
                        .get(TIMEOUT_SECONDS, TimeUnit.SECONDS);

                assertThat(firstExecutions.get()).isEqualTo(1);
                assertThat(secondExecutions.get()).isEqualTo(1);
                assertThat(queuedExecutions.get()).isEqualTo(1);
                assertThat(rejectedTaskExecuted.get()).isFalse();
            } finally {
                releaseRunningTasks.countDown();
            }
        });
    }

    @Test
    @DisplayName("컨텍스트 종료 후에는 새 알림 작업을 거부한다.")
    void rejectsNewTasksAfterContextClosed() {
        AtomicReference<TaskExecutor> executorReference = new AtomicReference<>();
        contextRunner.run(context -> executorReference.set(getExecutor(context)));

        TaskExecutor executor = executorReference.get();
        assertThat(executor).isNotNull();
        assertThatThrownBy(() -> executor.execute(() -> {}))
                .isInstanceOf(TaskRejectedException.class);
    }

    @Test
    @DisplayName("호출 스레드의 컨텍스트가 정리돼도 제출 당시 인증과 요청 ID를 전달한다.")
    void propagatesCallerContextToQueuedTask(){

        record TaskContext(Authentication authentication, String requestId){

        }

        contextRunner.withPropertyValues("everyone.notification.async.pool-size=1")
                .run(context ->{
                    TaskExecutor executor = getExecutor(context);
                    CountDownLatch started = new CountDownLatch(1);
                    CountDownLatch release = new CountDownLatch(1);

                    CompletableFuture.runAsync(() -> waitForRelease(started, release), executor);
                    try {
                        assertThat(started.await(TIMEOUT_SECONDS, TimeUnit.SECONDS)).isTrue();
                        Authentication authentication = UsernamePasswordAuthenticationToken
                                .authenticated("notification-test-user", null, List.of());
                        SecurityContext callerContext = SecurityContextHolder.createEmptyContext();
                        callerContext.setAuthentication(authentication);
                        SecurityContextHolder.setContext(callerContext);

                        MDC.put(MDCLoggingInterceptor.REQUEST_ID, "request-123");

                        CompletableFuture<TaskContext> queued = CompletableFuture.supplyAsync(() -> new TaskContext(SecurityContextHolder.getContext().getAuthentication(),
                                MDC.get(MDCLoggingInterceptor.REQUEST_ID)), executor);

                        callerContext.setAuthentication(null);
                        SecurityContextHolder.clearContext();
                        MDC.clear();
                        release.countDown();

                        TaskContext result = queued.get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
                        assertThat(result.authentication()).isEqualTo(authentication);
                        assertThat(result.requestId()).isEqualTo("request-123");

                    } finally {
                        SecurityContextHolder.clearContext();
                        MDC.clear();
                        release.countDown();
                    }
                });
    }

    private TaskExecutor getExecutor(AssertableApplicationContext context) {
        assertThat(context).hasNotFailed();
        assertThat(context).hasBean(EXECUTOR_BEAN_NAME);
        return context.getBean(EXECUTOR_BEAN_NAME, TaskExecutor.class);
    }

    private void waitForRelease(CountDownLatch started, CountDownLatch release) {
        started.countDown();
        try {
            if (!release.await(TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                throw new IllegalStateException("테스트 작업의 차단 해제 시간을 초과했습니다.");
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new CompletionException(exception);
        }
    }
}
