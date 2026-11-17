package com.codeit.sb13.playlist_for_everyone.domain.conversation.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.StringUtils;

import java.util.Arrays;

/**
 * DM 전송 방식과 Redis 채널을 검증한다. 기본값은 application.yaml에서 공급한다.
 * 현재 redis만 지원하며, Redis 설정과 공백이 아닌 채널은 자동 구독 여부와 관계없이 필요하다.
 * subscription-enabled는 별도의 빈 등록 조건이므로 이 record의 필드가 아니다.
 */
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
