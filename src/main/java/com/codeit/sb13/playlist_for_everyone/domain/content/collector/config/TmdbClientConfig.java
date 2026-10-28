package com.codeit.sb13.playlist_for_everyone.domain.content.collector.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(TmdbProperties.class)
public class TmdbClientConfig {

    @Bean
    public RestClient tmdbRestClient(
            RestClient.Builder builder,
            TmdbProperties properties
    ) {
        return builder
                .baseUrl(properties.getBaseUrl())
                .defaultHeader(
                        HttpHeaders.AUTHORIZATION,
                        "Bearer " + properties.getAccessToken()
                )
                .defaultHeader(
                        HttpHeaders.ACCEPT,
                        MediaType.APPLICATION_JSON_VALUE
                )
                .build();
    }
}