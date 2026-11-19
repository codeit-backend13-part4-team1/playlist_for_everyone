package com.codeit.sb13.playlist_for_everyone.domain.notification.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "everyone.notification.async")
public record NotificationAsyncProperties(
        int poolSize,
        int queueCapacity
) {
}
