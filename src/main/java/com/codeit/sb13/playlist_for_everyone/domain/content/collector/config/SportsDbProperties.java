package com.codeit.sb13.playlist_for_everyone.domain.content.collector.config;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

@Getter
@Setter
@Validated
@ConfigurationProperties(prefix = "everyone.sportsdb")
public class SportsDbProperties {

    @NotBlank
    private String baseUrl;

    @NotBlank(message = "TheSportsDB API 키는 필수입니다.")
    private String apiKey;

    private Duration connectTimeout = Duration.ofSeconds(3);
    private Duration readTimeout = Duration.ofSeconds(10);
}