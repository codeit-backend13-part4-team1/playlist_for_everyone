package com.codeit.sb13.playlist_for_everyone.domain.content.collector.batch;

import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.Step;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.infrastructure.repeat.RepeatStatus;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;

@Configuration(proxyBeanMethods = false)
public class TmdbBatchConfig {

    @Bean
    public Job tmdbCollectionJob(
            JobRepository jobRepository,
            @Qualifier("tmdbMovieCollectionStep") Step movieStep
    ) {
        return new JobBuilder("tmdbCollectionJob", jobRepository)
                .start(movieStep)
                .build();
    }

    @Bean
    public Step tmdbMovieCollectionStep(
            JobRepository jobRepository,
            PlatformTransactionManager transactionManager
    ) {
        return new StepBuilder("tmdbMovieCollectionStep", jobRepository)
                .tasklet(
                        (contribution, chunkContext) -> RepeatStatus.FINISHED,
                        transactionManager
                )
                .build();
    }
}