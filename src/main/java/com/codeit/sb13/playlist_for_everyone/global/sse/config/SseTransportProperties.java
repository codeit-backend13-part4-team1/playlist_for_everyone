package com.codeit.sb13.playlist_for_everyone.global.sse.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.StringUtils;

import java.util.Arrays;
import java.util.Locale;

/**
 * SSE 브로커 전송 방식과 Redis 채널을 검증한다. 현재 지원하는 전송 방식은 redis이다.
 * YAML의 기본값이 적용된 뒤에도 전송 방식이 없거나 지원하지 않는 값이면 시작에 실패한다.
 * Redis 설정과 공백이 아닌 채널도 필수이며, 자동 구독 활성화 여부는 구독 빈의 조건에서 처리한다.
 */
@ConfigurationProperties(prefix = "everyone.sse")
public record SseTransportProperties(
        String transport,
        Redis redis

) {
    public SseTransportProperties {
        if (!Transport.exist(transport)) {
            throw new IllegalArgumentException("지원하지 않는 SSE 전송 방식입니다. transport=%s".formatted(transport));
        }
        if (redis == null || !StringUtils.hasText(redis.channel())){
            throw new IllegalArgumentException("everyone.sse.redis 설정은 필수 입니다.");
        }
    }

    public record Redis(
            String channel
    ){

    }

    private enum Transport {
        REDIS;

        public static boolean exist(String transport) {
            if (transport == null) {
                return false;
            }

            String normalizedStatus = transport.toUpperCase(Locale.ROOT);
            return Arrays.stream(Transport.values())
                    .anyMatch(t -> t.name().equals(normalizedStatus));
        }
    }
}
