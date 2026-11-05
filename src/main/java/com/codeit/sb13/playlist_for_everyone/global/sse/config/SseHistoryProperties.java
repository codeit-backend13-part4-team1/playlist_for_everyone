package com.codeit.sb13.playlist_for_everyone.global.sse.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.StringUtils;

import java.time.Duration;

@ConfigurationProperties(prefix = "everyone.sse.history")
public record SseHistoryProperties(
        Duration retention,
        int maxEventsPerUser,
        String keyPrefix
) {
    public SseHistoryProperties {
        validateRetention(retention);
        validateKeyPrefix(keyPrefix);
        validateMaxEventsPerUser(maxEventsPerUser);
    }

    private void validateMaxEventsPerUser(int maxEventsPerUser){
        if (maxEventsPerUser >= 1){
            return;
        }
        throw new IllegalArgumentException("사용자별 최대 이벤트 건수는 1 이상이어야 합니다.");
    }

    private void validateKeyPrefix(String keyPrefix){
        if (StringUtils.hasText(keyPrefix)){
            return;
        }
        throw new IllegalArgumentException("SSE 이력 키 접두사는 null, 빈 문자열 또는 공백일 수 없습니다.");
    }

    private void validateRetention(Duration retention){
        if (retention == null){
            throw new IllegalArgumentException("SSE 이력 보관기간은 null일 수 없습니다.");
        }
        if (retention.isNegative()){
            throw new IllegalArgumentException("SSE 이력 보관기간은 음수일 수 없습니다.");
        }
        if (retention.isZero()){
            throw new IllegalArgumentException("SSE 이력 보관기간은 0일 수 없습니다.");
        }
        if (retention.compareTo(Duration.ofMillis(1)) < 0){
            throw new IllegalArgumentException("SSE 이력 보관기간은 1밀리초 이상이어야 합니다.");
        }
    }
}
