package com.codeit.sb13.playlist_for_everyone.global.config;

import com.codeit.sb13.playlist_for_everyone.domain.notification.config.NotificationAsyncProperties;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.ThreadPoolExecutor;

@Configuration(proxyBeanMethods = false)
@EnableAsync
@EnableConfigurationProperties(NotificationAsyncProperties.class)
public class NotificationAsyncConfig {

    @Bean(name = "notificationTaskContextDecorator", defaultCandidate = false)
    public TaskContextDecorator notificationTaskContextDecorator(){
        return new TaskContextDecorator();
    }

    /** 명시적으로 이름을 지정한 @Async 알림 작업의 동시 실행 수와 대기열을 제한한다. */
    @Bean(name = "notificationTaskExecutor", defaultCandidate = false)
    public ThreadPoolTaskExecutor notificationTaskExecutor(
            NotificationAsyncProperties properties,
            @Qualifier("notificationTaskContextDecorator")
            TaskContextDecorator taskDecorator) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(properties.poolSize());
        executor.setMaxPoolSize(properties.poolSize());
        executor.setQueueCapacity(properties.queueCapacity());
        executor.setThreadNamePrefix("notification-");
        // 포화 시 호출 스레드가 대신 처리하지 않으며, 제출자에게 거부 예외를 전달한다.
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.AbortPolicy());
        // 전용 실행기에서 작업을 실행할 때 제출 시점의 인증과 MDC를 적용한다.
        executor.setTaskDecorator(taskDecorator);
        return executor;
    }
}
