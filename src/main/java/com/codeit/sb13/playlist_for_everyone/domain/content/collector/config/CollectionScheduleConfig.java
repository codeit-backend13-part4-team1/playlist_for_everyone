package com.codeit.sb13.playlist_for_everyone.domain.content.collector.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(
        prefix = "everyone.collection.schedule",
        name = "enabled",
        havingValue = "true",
        matchIfMissing = false
)
public class CollectionScheduleConfig {

    @Bean(name = "contentCollectionTaskScheduler", defaultCandidate = false)
    public ThreadPoolTaskScheduler contentCollectionTaskScheduler() {
        var scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(1);
        scheduler.setThreadNamePrefix("content-collection-");
        return scheduler;
    }
}