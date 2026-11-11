package com.codeit.sb13.playlist_for_everyone.domain.content.collector.batch;

import com.codeit.sb13.playlist_for_everyone.domain.content.collector.ContentCollectionService;
import com.codeit.sb13.playlist_for_everyone.domain.content.collector.SportsDbClient;
import com.codeit.sb13.playlist_for_everyone.domain.content.collector.SportsDbContentMapper;
import com.codeit.sb13.playlist_for_everyone.domain.content.collector.dto.CollectedContentData;
import com.codeit.sb13.playlist_for_everyone.domain.content.collector.dto.SportsDbEventResponse;
import com.codeit.sb13.playlist_for_everyone.domain.content.collector.exception.CollectedContentValidationException;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.job.parameters.InvalidJobParametersException;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.Step;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.util.StringUtils;

@Configuration(proxyBeanMethods = false)
public class SportsDbBatchConfig {

    @Bean
    public Job sportsDbCollectionJob(
            JobRepository jobRepository,
            @Qualifier("sportsDbCollectionStep") Step step
    ) {
        return new JobBuilder("sportsDbCollectionJob", jobRepository)
                .validator(parameters -> {
                    var leagueId = parameters.getParameter("leagueId");

                    if (leagueId == null
                            || !String.class.equals(leagueId.type())
                            || !StringUtils.hasText((String) leagueId.value())) {
                        throw new InvalidJobParametersException(
                                "leagueId는 필수입니다."
                        );
                    }

                    var season = parameters.getParameter("season");

                    if (season == null
                            || !String.class.equals(season.type())
                            || !StringUtils.hasText((String) season.value())) {
                        throw new InvalidJobParametersException(
                                "season은 필수입니다."
                        );
                    }
                })
                .start(step)
                .build();
    }

    @Bean
    @StepScope
    public SportsDbEventReader sportsDbEventReader(
            SportsDbClient client,
            @Value("#{jobParameters['leagueId']}") String leagueId,
            @Value("#{jobParameters['season']}") String season
    ) {
        return new SportsDbEventReader(client, leagueId, season);
    }

    @Bean
    public Step sportsDbCollectionStep(
            JobRepository jobRepository,
            PlatformTransactionManager transactionManager,
            @Qualifier("sportsDbEventReader") SportsDbEventReader reader,
            ContentCollectionService collectionService
    ) {
        var mapper = new SportsDbContentMapper();

        return new StepBuilder("sportsDbCollectionStep", jobRepository)
                .<SportsDbEventResponse, CollectedContentData>chunk(1)
                .transactionManager(transactionManager)
                .faultTolerant()
                .skip(CollectedContentValidationException.class)
                .skipLimit(10)
                .reader(reader)
                .processor(mapper::fromEvent)
                .writer(chunk -> {
                    for (var data : chunk.getItems()) {
                        collectionService.save(data);
                    }
                })
                .build();
    }
}