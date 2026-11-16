package com.codeit.sb13.playlist_for_everyone.domain.content.collector.config;

import com.codeit.sb13.playlist_for_everyone.domain.content.collector.batch.CollectionJobLauncher;
import com.codeit.sb13.playlist_for_everyone.domain.content.collector.batch.CollectionScheduler;
import org.springframework.batch.core.launch.JobOperator;
import org.springframework.batch.core.job.Job;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

@EnableConfigurationProperties(CollectionScheduleProperties.class)
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

    @Bean
    public CollectionJobLauncher collectionJobLauncher(
            JobOperator jobOperator,
            @Qualifier("tmdbCollectionJob") Job tmdbCollectionJob,
            @Qualifier("sportsDbCollectionJob") Job sportsDbCollectionJob
    ) {
        return new CollectionJobLauncher(
                jobOperator,
                tmdbCollectionJob,
                sportsDbCollectionJob
        );
    }

    @Bean
    public CollectionScheduler collectionScheduler(
            CollectionJobLauncher launcher,
            CollectionScheduleProperties properties
    ) {
        return new CollectionScheduler(launcher, properties);
    }
}