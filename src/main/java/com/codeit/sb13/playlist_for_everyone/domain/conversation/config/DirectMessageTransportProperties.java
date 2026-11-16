package com.codeit.sb13.playlist_for_everyone.domain.conversation.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "everyone.dm.websocket")
public record DirectMessageTransportProperties(
        String transport,
        Redis redis
) {
    public record Redis(String channel){

    }
}
