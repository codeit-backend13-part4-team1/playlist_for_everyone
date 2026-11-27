package com.codeit.sb13.playlist_for_everyone.global.sse.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "everyone.sse.heartbeat")
public record SseHeartbeatProperties(int poolSize, int queueCapacity) {

    public SseHeartbeatProperties {
        if (poolSize < 2) {
            throw new IllegalArgumentException("everyone.sse.heartbeat.pool-size는 2 이상이어야 합니다.");
        }

        if (queueCapacity < 0) {
            throw new IllegalArgumentException("everyone.sse.heartbeat.queue-capacity는 0 이상이어야 합니다.");
        }
    }
}
