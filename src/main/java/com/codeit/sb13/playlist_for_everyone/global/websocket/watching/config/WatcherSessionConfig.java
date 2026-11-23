package com.codeit.sb13.playlist_for_everyone.global.websocket.watching.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
public class WatcherSessionConfig {

    @Bean(name = "watchingSessionExecutor")
    public ThreadPoolTaskExecutor sendMessageExecutor(){
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(8);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("watching-session-message-executor-");
        executor.initialize();
        return executor;
    }

}
