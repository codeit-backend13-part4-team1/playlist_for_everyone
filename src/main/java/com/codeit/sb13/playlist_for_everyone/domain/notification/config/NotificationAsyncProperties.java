package com.codeit.sb13.playlist_for_everyone.domain.notification.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "everyone.notification.async")
public record NotificationAsyncProperties(
        int poolSize,
        int queueCapacity
) {
    public NotificationAsyncProperties {
        if (poolSize < 1) {
            throw new IllegalArgumentException("알림 실행기 동시 실행 수는 1 이상이어야 합니다.");
        }
        if (queueCapacity < 0) {
            throw new IllegalArgumentException("알림 실행기 대기열 크기는 0 이상이어야 합니다.");
        }
    }
}
