package com.codeit.sb13.playlist_for_everyone.global.config;

import com.codeit.sb13.playlist_for_everyone.domain.notification.config.NotificationAsyncProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.ThreadPoolExecutor;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(NotificationAsyncProperties.class)
public class NotificationAsyncConfig {

    /** 알림 작업의 동시 실행 수와 대기열을 제한하고, 기본 실행기의 선택에 영향을 주지 않는다. */
    @Bean(name = "notificationTaskExecutor", defaultCandidate = false)
    public ThreadPoolTaskExecutor notificationTaskExecutor(NotificationAsyncProperties properties) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(properties.poolSize());
        executor.setMaxPoolSize(properties.poolSize());
        executor.setQueueCapacity(properties.queueCapacity());
        executor.setThreadNamePrefix("notification-");
        // 포화 시 호출 스레드가 대신 처리하지 않으며, 제출자에게 거부 예외를 전달한다.
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.AbortPolicy());
        return executor;
    }
}
