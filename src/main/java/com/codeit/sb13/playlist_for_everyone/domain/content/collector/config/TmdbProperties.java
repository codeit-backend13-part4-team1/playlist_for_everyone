package com.codeit.sb13.playlist_for_everyone.domain.content.collector.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@Getter
@Setter
@ConfigurationProperties(prefix = "everyone.tmdb")
public class TmdbProperties {

    private String baseUrl;
    private String accessToken;
    private Duration connectTimeout = Duration.ofSeconds(3);
    private Duration readTimeout = Duration.ofSeconds(10);
}