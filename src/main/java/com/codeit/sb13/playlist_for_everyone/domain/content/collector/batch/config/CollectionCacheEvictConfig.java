package com.codeit.sb13.playlist_for_everyone.domain.content.collector.batch.config;

import com.codeit.sb13.playlist_for_everyone.domain.content.collector.batch.listener.CollectionCacheEvictJobListener;
import com.codeit.sb13.playlist_for_everyone.domain.content.collector.batch.CollectionInsertCounter;
import org.springframework.batch.core.configuration.annotation.JobScope;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class CollectionCacheEvictConfig {

    @Bean
    @JobScope
    public CollectionInsertCounter collectionInsertCounter() {
        return new CollectionInsertCounter();
    }

    @Bean
    public CollectionCacheEvictJobListener collectionCacheEvictJobListener(
            CollectionInsertCounter counter,
            ApplicationEventPublisher eventPublisher
    ) {
        return new CollectionCacheEvictJobListener(counter, eventPublisher);
    }
}