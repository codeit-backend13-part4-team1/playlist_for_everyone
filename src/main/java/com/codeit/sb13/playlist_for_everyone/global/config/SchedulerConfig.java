package com.codeit.sb13.playlist_for_everyone.global.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

@Configuration(proxyBeanMethods = false)
@EnableScheduling
public class SchedulerConfig {

    @Bean(name = "sseHeartbeatTaskScheduler", defaultCandidate = false)
    public ThreadPoolTaskScheduler sseHeartbeatTaskScheduler(){
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(1);
        scheduler.setThreadNamePrefix("sse-heartbeat-");
        return scheduler;
    }
}
