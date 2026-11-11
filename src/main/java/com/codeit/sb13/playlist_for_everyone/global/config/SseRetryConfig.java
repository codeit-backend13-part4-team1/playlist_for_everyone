package com.codeit.sb13.playlist_for_everyone.global.config;

import com.codeit.sb13.playlist_for_everyone.global.sse.config.SseRetryProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

/**
 * 후속 조회 재시도 전용 실행기를 제공한다. 하트비트 실행기와 이름으로 구분한다.
 * 취소된 예약을 큐에서 제거하고 종료 후 대기 작업을 실행하지 않는다.
 * 실행 중인 모든 작업이 ContextClosedEvent 전에 끝났다는 보장은 아니다.
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(SseRetryProperties.class)
public class SseRetryConfig {

    @Bean(name = "sseRetryTaskScheduler", defaultCandidate = false)
    public ThreadPoolTaskScheduler sseRetryTaskScheduler(){
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(2);
        scheduler.setThreadNamePrefix("sse-retry-");
        scheduler.setRemoveOnCancelPolicy(true);
        scheduler.setExecuteExistingDelayedTasksAfterShutdownPolicy(false);
        return scheduler;
    }

}
