package com.codeit.sb13.playlist_for_everyone.domain.notification.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationListener;
import org.springframework.context.event.ContextClosedEvent;
import org.springframework.core.Ordered;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.List;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 알림 실행기의 신규 제출을 차단하고, 접수된 작업이 자원 정리 전에 완료될 시간을 제공한다.
 *
 * <p>종료 이벤트를 동기적으로 처리하여 DB 빈 소멸과 일반 종료 리스너보다 먼저 대기한다.
 * 정상 대기 중에는 수신자 순회를 허용한다. 제한 초과나 종료 대기의 인터럽트가 발생한 경우에만
 * 중단 상태를 설정하고 대기 작업 취소·실행 작업 인터럽트를 요청한다.
 * 인터럽트를 무시하는 외부 I/O의 즉시 종료나 미완료 알림의 전달까지 보장하지는 않는다.
 */
@RequiredArgsConstructor
@Slf4j
public class NotificationExecutorLifecycle implements ApplicationListener<ContextClosedEvent>, Ordered {

    private final ThreadPoolTaskExecutor executor;
    private final NotificationAsyncProperties properties;
    private final ApplicationContext applicationContext;
    private final AtomicBoolean shutdownStarted = new AtomicBoolean();
    private volatile boolean stopRequested;

    @Override
    public void onApplicationEvent(ContextClosedEvent event) {
        if (event.getApplicationContext() != applicationContext || !shutdownStarted.compareAndSet(false, true)) {
            return;
        }

        // shutdown은 신규 제출만 거부하며 이미 접수된 대기 작업은 계속 실행한다.
        executor.initiateShutdown();
        try {
            if (!executor.getThreadPoolExecutor().awaitTermination(
                    properties.shutdownTimeout().toNanos(), TimeUnit.NANOSECONDS)) {
                requestStop("종료 대기 시간 초과");
            }
        } catch (InterruptedException failure) {
            requestStop("종료 대기 중 인터럽트");
            Thread.currentThread().interrupt();
        }
    }

    private void requestStop(String reason) {
        // 하위 호출이 인터럽트 상태를 해제하더라도 다음 수신자 발행을 중단할 수 있게 유지한다.
        stopRequested = true;
        List<Runnable> remainingTasks = executor.getThreadPoolExecutor().shutdownNow();
        for (Runnable task : remainingTasks) {
            if (task instanceof Future<?> future) {
                future.cancel(true);
            }
        }
        log.warn("알림 실행기 중단 요청. reason={}, timeout={}, activeCount={}, cancelledTaskCount={}",
                reason, properties.shutdownTimeout(), executor.getActiveCount(), remainingTasks.size());
    }

    public boolean isStopRequested() {
        return stopRequested;
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }

    @Override
    public boolean supportsAsyncExecution() {
        return false;
    }
}
