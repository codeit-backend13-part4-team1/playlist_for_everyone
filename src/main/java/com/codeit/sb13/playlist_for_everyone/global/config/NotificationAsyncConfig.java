package com.codeit.sb13.playlist_for_everyone.global.config;

import com.codeit.sb13.playlist_for_everyone.domain.notification.config.NotificationAsyncProperties;
import com.codeit.sb13.playlist_for_everyone.domain.notification.config.NotificationExecutorLifecycle;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.ThreadPoolExecutor;

@Configuration(proxyBeanMethods = false)
@EnableAsync
@EnableConfigurationProperties(NotificationAsyncProperties.class)
public class NotificationAsyncConfig {

    /** 명시적으로 이름을 지정한 @Async 알림 작업의 동시 실행 수와 대기열을 제한한다. */
    @Bean(name = "notificationTaskExecutor", defaultCandidate = false)
    public ThreadPoolTaskExecutor notificationTaskExecutor(NotificationAsyncProperties properties) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(properties.poolSize());
        executor.setMaxPoolSize(properties.poolSize());
        executor.setQueueCapacity(properties.queueCapacity());
        executor.setThreadNamePrefix("notification-");
        // 포화 시 호출 스레드가 대신 처리하지 않으며, 제출자에게 거부 예외를 전달한다.
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.AbortPolicy());
        // 종료 이벤트에서 Lifecycle이 먼저 제출을 차단하고 제한 시간까지 대기한다.
        // Spring의 별도 stop 단계가 대기 작업을 멈추지 않도록 하며, 대기 시간은 중복 설정하지 않는다.
        executor.setWaitForTasksToCompleteOnShutdown(true);
        return executor;
    }

    @Bean
    public NotificationExecutorLifecycle notificationExecutorLifecycle(
            @Qualifier("notificationTaskExecutor") ThreadPoolTaskExecutor executor,
            NotificationAsyncProperties properties,
            ApplicationContext applicationContext) {
        return new NotificationExecutorLifecycle(executor, properties, applicationContext);
    }
}
