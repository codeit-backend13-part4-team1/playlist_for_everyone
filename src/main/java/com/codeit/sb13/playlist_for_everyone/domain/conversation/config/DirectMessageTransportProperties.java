package com.codeit.sb13.playlist_for_everyone.domain.conversation.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.StringUtils;

import java.util.Arrays;

@ConfigurationProperties(prefix = "everyone.dm.websocket")
public record DirectMessageTransportProperties(
        String transport,
        Redis redis
) {

    public record Redis(String channel){

    }

    public DirectMessageTransportProperties {
        if (!Transport.isValid(transport)) {
            throw new IllegalArgumentException( "지원하지 않는 DM 전송 방식입니다. transport=%s".formatted(transport));
        }

        if (redis == null || !StringUtils.hasText(redis.channel())) {
            throw new IllegalArgumentException("DM Redis 설정과 공백이 아닌 채널은 필수입니다.");
        }
    }

    private enum Transport{
        REDIS;

        public static boolean isValid(String transport){
            return Arrays.stream(Transport.values())
                    .anyMatch(t -> t.name().equalsIgnoreCase(transport));
        }
    }
}
