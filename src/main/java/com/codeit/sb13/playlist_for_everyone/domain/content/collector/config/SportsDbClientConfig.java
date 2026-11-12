package com.codeit.sb13.playlist_for_everyone.domain.content.collector.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;


@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(SportsDbProperties.class)
public class SportsDbClientConfig {

    /** TheSportsDB API 요청에 사용할 클라이언트 생성 */
    @Bean
    public RestClient sportsDbRestClient(
            RestClient.Builder builder,
            SportsDbProperties properties
    ) {
        var requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(properties.getConnectTimeout());
        requestFactory.setReadTimeout(properties.getReadTimeout());

        return builder
                .requestFactory(requestFactory)
                .baseUrl(properties.getBaseUrl() + "/" + properties.getApiKey())
                .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                .build();
    }
}