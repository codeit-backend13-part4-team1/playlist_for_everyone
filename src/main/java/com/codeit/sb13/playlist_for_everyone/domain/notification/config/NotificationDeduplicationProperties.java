package com.codeit.sb13.playlist_for_everyone.domain.notification.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "everyone.notification.deduplication")
public record NotificationDeduplicationProperties(Duration window) {

    public NotificationDeduplicationProperties{
        if (window ==null || window.compareTo(Duration.ofMillis(1)) < 0){
            throw new IllegalArgumentException("반복 알림 제한 시간은 1밀리초 이상이어야 합니다.");
        }
    }
}
