package com.codeit.sb13.playlist_for_everyone.global.sse.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.Arrays;
import java.util.Locale;

@ConfigurationProperties(prefix = "everyone.sse")
public record SseTransportProperties(
        String transport,
        Redis redis

) {
    public SseTransportProperties {
        if (!Transport.exist(transport)) {
            throw new IllegalArgumentException("지원하지 않는 SSE 전송 방식입니다. transport=%s".formatted(transport));
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
