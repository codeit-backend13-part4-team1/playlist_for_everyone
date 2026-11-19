package com.codeit.sb13.playlist_for_everyone.global.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.*;
import static org.awaitility.Awaitility.await;

@DisplayName("알림 실행기의 제한 시간 종료")
class NotificationExecutorShutdownTest {
    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(NotificationAsyncConfig.class, Resources.class)
            .withPropertyValues("everyone.notification.async.pool-size=1",
                    "everyone.notification.async.queue-capacity=2",
                    "everyone.notification.async.shutdown-timeout=2s");

    @Test
    @DisplayName("종료 중 신규 제출을 차단하고 실행·대기 작업 완료 후 다른 빈을 정리한다.")
    void drainsAcceptedWorkBeforeDestroyingResources() {
        runner.run(context -> {
            var executor = context.getBean("notificationTaskExecutor", ThreadPoolTaskExecutor.class);
            var resource = context.getBean(Resource.class);
            CountDownLatch started = new CountDownLatch(1);
            CountDownLatch release = new CountDownLatch(1);
            AtomicInteger completed = new AtomicInteger();
            ExecutorService closer = Executors.newSingleThreadExecutor();
            Future<?> closing = null;
            try {
                Future<?> first = executor.submit(() -> {
                    started.countDown();
                    awaitRelease(release);
                    assertThat(resource.destroyed.get()).isFalse();
                    completed.incrementAndGet();
                });
                assertThat(started.await(2, TimeUnit.SECONDS)).isTrue();
                Future<?> queued = executor.submit(() -> {
                    assertThat(resource.destroyed.get()).isFalse();
                    completed.incrementAndGet();
                });
                closing = closer.submit(() -> context.getSourceApplicationContext().close());
                await().atMost(1, TimeUnit.SECONDS).until(() -> executor.getThreadPoolExecutor().isShutdown());
                assertThatThrownBy(() -> executor.execute(() -> {})).isInstanceOf(TaskRejectedException.class);
                assertThat(resource.destroyed.get()).isFalse();
                release.countDown();
                closing.get(3, TimeUnit.SECONDS);
                first.get(1, TimeUnit.SECONDS);
                queued.get(1, TimeUnit.SECONDS);
                assertThat(completed).hasValue(2);
                assertThat(resource.destroyed).isTrue();
                assertThat(executor.getThreadPoolExecutor().isTerminated()).isTrue();
            } finally {
                release.countDown();
                if (closing != null) closing.get(5, TimeUnit.SECONDS);
                closer.shutdownNow();
                assertThat(closer.awaitTermination(2, TimeUnit.SECONDS)).isTrue();
            }
        });
    }

    @Test
    @DisplayName("종료 제한을 넘으면 실행 작업에 중단을 요청하고 대기 작업은 실행하지 않고 취소한다.")
    void cancelsRemainingWorkAfterDeadline() {
        runner.withPropertyValues("everyone.notification.async.shutdown-timeout=100ms").run(context -> {
            var executor = context.getBean("notificationTaskExecutor", ThreadPoolTaskExecutor.class);
            CountDownLatch started = new CountDownLatch(1);
            CountDownLatch release = new CountDownLatch(1);
            AtomicBoolean interrupted = new AtomicBoolean();
            AtomicBoolean queuedRan = new AtomicBoolean();
            ExecutorService closer = Executors.newSingleThreadExecutor();
            Future<?> closing = null;
            try {
                executor.submit(() -> {
                    started.countDown();
                    try {
                        release.await(10, TimeUnit.SECONDS);
                    } catch (InterruptedException failure) {
                        interrupted.set(true);
                        Thread.currentThread().interrupt();
                    }
                });
                assertThat(started.await(2, TimeUnit.SECONDS)).isTrue();
                Future<?> queued = executor.submit(() -> queuedRan.set(true));
                closing = closer.submit(() -> context.getSourceApplicationContext().close());
                closing.get(3, TimeUnit.SECONDS);
                await().atMost(1, TimeUnit.SECONDS).untilTrue(interrupted);
                assertThat(queued.isCancelled()).isTrue();
                assertThat(queuedRan).isFalse();
                await().atMost(1, TimeUnit.SECONDS).until(() -> executor.getThreadPoolExecutor().isTerminated());
            } finally {
                release.countDown();
                if (closing != null) closing.get(5, TimeUnit.SECONDS);
                closer.shutdownNow();
                assertThat(closer.awaitTermination(2, TimeUnit.SECONDS)).isTrue();
            }
        });
    }

    @ParameterizedTest
    @ValueSource(strings = {"0s", "-1s"})
    @DisplayName("0 이하의 종료 대기 시간은 기동 시 거부한다.")
    void rejectsInvalidShutdownTimeout(String value) {
        runner.withPropertyValues("everyone.notification.async.shutdown-timeout=" + value)
                .run(context -> assertThat(context).hasFailed());
    }

    private static void awaitRelease(CountDownLatch release) {
        try {
            if (!release.await(10, TimeUnit.SECONDS)) throw new AssertionError("테스트 차단 해제 시간 초과");
        } catch (InterruptedException failure) {
            Thread.currentThread().interrupt();
            throw new AssertionError("정상 종료 대기 중 작업을 중단하면 안 됩니다.", failure);
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class Resources {
        @Bean Resource resource() { return new Resource(); }
    }
    static class Resource implements DisposableBean {
        final AtomicBoolean destroyed = new AtomicBoolean();
        @Override public void destroy() { destroyed.set(true); }
    }
}

