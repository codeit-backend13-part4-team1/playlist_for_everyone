package com.codeit.sb13.playlist_for_everyone.domain.notification.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;

@ConfigurationProperties(prefix = "everyone.notification.async")
public record NotificationAsyncProperties(
        int poolSize,
        int queueCapacity,
        @DefaultValue("30s") Duration shutdownTimeout
) {
    public NotificationAsyncProperties {
        if (poolSize < 1) {
            throw new IllegalArgumentException("알림 실행기 동시 실행 수는 1 이상이어야 합니다.");
        }
        if (queueCapacity < 0) {
            throw new IllegalArgumentException("알림 실행기 대기열 크기는 0 이상이어야 합니다.");
        }
        if (shutdownTimeout == null || shutdownTimeout.isZero() || shutdownTimeout.isNegative()) {
            throw new IllegalArgumentException("알림 실행기 종료 대기 시간은 0보다 커야 합니다.");
        }
    }
}
