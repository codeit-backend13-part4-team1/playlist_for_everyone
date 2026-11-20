package com.codeit.sb13.playlist_for_everyone.domain.notification.config;

import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.List;

public class NotificationTaskExecutor extends ThreadPoolTaskExecutor {

    /**
     * 실행 중인 작업에 인터럽트를 요청하고, 큐에서 꺼낸 대기 작업을 취소한다.
     * {@link #cancelRemainingTask(Runnable)}은 데코레이터가 감싼 작업에 연결된 원본 {@code Future}까지 취소한다.
     *
     * @return 큐에서 제거한 대기 작업 수
     */
    int shutdownNowAndCancelPendingTasks(){
        List<Runnable> pendingTasks = getThreadPoolExecutor().shutdownNow();
        pendingTasks.forEach(this::cancelRemainingTask);
        return pendingTasks.size();
    }
}
