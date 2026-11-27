package com.codeit.sb13.playlist_for_everyone.domain.content.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.ThreadPoolExecutor;

@Configuration(proxyBeanMethods = false)
public class ContentIndexAsyncConfig {

    /**
     * 콘텐츠 색인 작업의 동시 실행 수와 대기열을 제한한다.
     */
    @Bean(name = "contentIndexTaskExecutor", defaultCandidate = false)
    public ThreadPoolTaskExecutor contentIndexTaskExecutor(
            ContentIndexAsyncProperties properties
    ) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();

        executor.setCorePoolSize(properties.poolSize());
        executor.setMaxPoolSize(properties.poolSize());
        executor.setQueueCapacity(properties.queueCapacity());
        executor.setThreadNamePrefix("content-index-");

        // 포화 시 요청 스레드에서 색인 작업을 직접 실행하지 않는다.
        executor.setRejectedExecutionHandler(
                new ThreadPoolExecutor.AbortPolicy()
        );

        return executor;
    }

}