package com.codeit.sb13.playlist_for_everyone.domain.content.collector.batch.config;

import com.codeit.sb13.playlist_for_everyone.domain.content.collector.batch.EcsTaskMetadataClient;
import com.codeit.sb13.playlist_for_everyone.domain.content.collector.batch.listener.CollectionExecutionOwnerListener;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.time.Duration;

@Configuration(proxyBeanMethods = false)
public class EcsTaskMetadataConfig {

    @Bean
    public EcsTaskMetadataClient ecsTaskMetadataClient(
            RestClient.Builder builder,
            @Value("${everyone.collection.ecs.connect-timeout:1s}")
            Duration connectTimeout,
            @Value("${everyone.collection.ecs.read-timeout:2s}")
            Duration readTimeout
    ) {
        var requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(connectTimeout);
        requestFactory.setReadTimeout(readTimeout);

        var restClient = builder
                .requestFactory(requestFactory)
                .defaultHeader(
                        HttpHeaders.ACCEPT,
                        MediaType.APPLICATION_JSON_VALUE
                )
                .build();

        return new EcsTaskMetadataClient(restClient);
    }

    @Bean
    public CollectionExecutionOwnerListener collectionExecutionOwnerListener(
            EcsTaskMetadataClient metadataClient,
            JobRepository jobRepository,
            @Value("${ECS_CONTAINER_METADATA_URI_V4:}") String metadataUri
    ) {
        return new CollectionExecutionOwnerListener(
                () -> metadataClient.getCurrentOwner(metadataUri),
                jobRepository
        );
    }
}