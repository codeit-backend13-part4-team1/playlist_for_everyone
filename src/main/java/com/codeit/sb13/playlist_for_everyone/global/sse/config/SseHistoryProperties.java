package com.codeit.sb13.playlist_for_everyone.global.sse.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "everyone.sse.history")
public record SseHistoryProperties(
        Duration retention,
        int maxEventsPerUser,
        String keyPrefix
) {
}
