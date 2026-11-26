package com.codeit.sb13.playlist_for_everyone.global.config;

import com.codeit.sb13.playlist_for_everyone.global.sse.config.SseHeartbeatProperties;
import com.codeit.sb13.playlist_for_everyone.global.sse.repository.SseEmitterRepository;
import com.codeit.sb13.playlist_for_everyone.global.sse.service.SseDeliveryCoordinator;
import com.codeit.sb13.playlist_for_everyone.global.sse.service.SseHeartbeatDispatcher;
import com.codeit.sb13.playlist_for_everyone.global.sse.service.SseHeartbeatWorker;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.ThreadPoolExecutor;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(SseHeartbeatProperties.class)
@EnableAsync
public class SseHeartbeatConfig {
    @Bean(name = "sseHeartbeatTaskExecutor", defaultCandidate = false)
    public ThreadPoolTaskExecutor sseHeartbeatTaskExecutor(SseHeartbeatProperties properties) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();

        executor.setCorePoolSize(properties.poolSize());
        executor.setMaxPoolSize(properties.poolSize());
        executor.setQueueCapacity(properties.queueCapacity());
        executor.setThreadNamePrefix("sse-heartbeat-worker-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.AbortPolicy());
        // 종료 이벤트에서 디스패처가 실행기를 직접 종료하므로 Spring의 워커 완료 대기를 건너뛴다.
        executor.setAcceptTasksAfterContextClose(true);

        return executor;
    }

    @Bean
    public SseHeartbeatWorker heartbeatWorker(
            SseEmitterRepository repository, SseDeliveryCoordinator deliveryCoordinator) {
        return new SseHeartbeatWorker(repository, deliveryCoordinator);
    }

    @Bean
    public SseHeartbeatDispatcher sseHeartbeatDispatcher(
            SseHeartbeatWorker heartbeatWorker,
            SseEmitterRepository repository,
            @Qualifier("sseHeartbeatTaskExecutor")
            ThreadPoolTaskExecutor heartbeatExecutor) {
        return new SseHeartbeatDispatcher(
                heartbeatWorker,
                repository,
                heartbeatExecutor);
    }
}
