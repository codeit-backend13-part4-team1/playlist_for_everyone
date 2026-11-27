package com.codeit.sb13.playlist_for_everyone.domain.content.config;

import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "everyone.elasticsearch.async")
public record ContentIndexAsyncProperties(
        @Min(1) int poolSize,
        @Min(1) int queueCapacity
) {
}
