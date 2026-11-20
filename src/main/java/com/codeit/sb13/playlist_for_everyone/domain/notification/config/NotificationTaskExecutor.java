package com.codeit.sb13.playlist_for_everyone.domain.notification.config;

import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.List;

public class NotificationTaskExecutor extends ThreadPoolTaskExecutor {

    int shutdownNowAndCancelPendingTasks(){
        List<Runnable> pendingTasks = getThreadPoolExecutor().shutdownNow();
        pendingTasks.forEach(this::cancelRemainingTask);
        return pendingTasks.size();
    }
}
