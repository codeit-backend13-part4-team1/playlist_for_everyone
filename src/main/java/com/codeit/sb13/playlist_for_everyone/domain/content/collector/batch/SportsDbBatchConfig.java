package com.codeit.sb13.playlist_for_everyone.domain.content.collector.batch;

import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.infrastructure.repeat.RepeatStatus;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;

@Configuration(proxyBeanMethods = false)
public class SportsDbBatchConfig {

    @Bean
    public Job sportsDbCollectionJob(
            JobRepository jobRepository,
            PlatformTransactionManager transactionManager
    ) {
        var step = new StepBuilder(
                "sportsDbCollectionStep", jobRepository
        )
                .tasklet((contribution, chunkContext) -> RepeatStatus.FINISHED)
                .transactionManager(transactionManager)
                .build();

        return new JobBuilder("sportsDbCollectionJob", jobRepository)
                .start(step)
                .build();
    }
}