package com.codeit.sb13.playlist_for_everyone.global.sse.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "everyone.sse")
public record SseTransportProperties(
        String transport,
        Redis redis

) {
    public record Redis(
            String channel
    ){

    }
}
