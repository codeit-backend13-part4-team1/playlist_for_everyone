package com.codeit.sb13.playlist_for_everyone.domain.content.collector.config;

import com.codeit.sb13.playlist_for_everyone.domain.content.collector.batch.CollectionExecutionRecoveryPolicy;
import com.codeit.sb13.playlist_for_everyone.domain.content.collector.batch.CollectionJobLauncher;
import com.codeit.sb13.playlist_for_everyone.domain.content.collector.batch.CollectionJobLock;
import com.codeit.sb13.playlist_for_everyone.domain.content.collector.batch.CollectionScheduler;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.launch.JobOperator;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

import javax.sql.DataSource;

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
    public CollectionJobLock collectionJobLock(DataSource dataSource) {
        return new CollectionJobLock(dataSource);
    }

    @Bean
    public CollectionJobLauncher collectionJobLauncher(
            JobOperator jobOperator,
            @Qualifier("tmdbCollectionJob") Job tmdbCollectionJob,
            @Qualifier("sportsDbCollectionJob") Job sportsDbCollectionJob,
            JobRepository jobRepository,
            CollectionJobLock jobLock,
            CollectionExecutionRecoveryPolicy recoveryPolicy
    ) {
        return new CollectionJobLauncher(
                jobOperator,
                tmdbCollectionJob,
                sportsDbCollectionJob,
                jobRepository,
                jobLock,
                recoveryPolicy
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