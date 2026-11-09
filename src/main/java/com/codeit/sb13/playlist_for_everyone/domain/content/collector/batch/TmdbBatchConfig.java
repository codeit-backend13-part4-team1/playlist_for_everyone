package com.codeit.sb13.playlist_for_everyone.domain.content.collector.batch;

import com.codeit.sb13.playlist_for_everyone.domain.content.collector.ContentCollectionService;
import com.codeit.sb13.playlist_for_everyone.domain.content.collector.TmdbClient;
import com.codeit.sb13.playlist_for_everyone.domain.content.collector.TmdbContentMapper;
import com.codeit.sb13.playlist_for_everyone.domain.content.collector.dto.CollectedContentData;
import com.codeit.sb13.playlist_for_everyone.domain.content.collector.dto.TmdbMovieResponse;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.Step;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
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
    @StepScope
    public TmdbMovieReader tmdbMovieReader(
            TmdbClient tmdbClient,
            @Value("#{jobParameters['maxPages']}") Integer maxPages
    ) {
        if (maxPages == null) {
            throw new IllegalArgumentException(
                    "수집할 페이지 수는 필수입니다."
            );
        }

        return new TmdbMovieReader(tmdbClient, maxPages);
    }

    @Bean
    public Step tmdbMovieCollectionStep(
            JobRepository jobRepository,
            PlatformTransactionManager transactionManager,
            @Qualifier("tmdbMovieReader") TmdbMovieReader reader,
            ContentCollectionService collectionService
    ) {
        var mapper = new TmdbContentMapper();

        return new StepBuilder("tmdbMovieCollectionStep", jobRepository)
                .<TmdbMovieResponse, CollectedContentData>chunk(1)
                .transactionManager(transactionManager)
                .reader(reader)
                .processor(mapper::fromMovie)
                .writer(chunk -> {
                    for (var data : chunk.getItems()) {
                        collectionService.save(data);
                    }
                })
                .build();
    }
}