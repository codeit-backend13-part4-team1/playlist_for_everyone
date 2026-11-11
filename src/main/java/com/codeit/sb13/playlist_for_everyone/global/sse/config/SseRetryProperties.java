package com.codeit.sb13.playlist_for_everyone.global.sse.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * 연결을 유지한 채 수행하는 후속 조회 재시도 설정. 브로커 재발행이나 브라우저 재연결 설정이 아니다.
 * maxRetries는 최초 시도를 제외한 예약 횟수이며, 정상 조회 후 횟수와 간격을 초기화한다.
 */
@ConfigurationProperties(prefix = "everyone.sse.retry")
public record SseRetryProperties(
        Duration initialDelay,
        Duration maxDelay,
        int maxRetries
) {
    public SseRetryProperties {
        validateDuration(initialDelay, "초기 재시도 간격");
        validateDuration(maxDelay, "최대 재시도 간격");
        if (initialDelay.compareTo(maxDelay) > 0){
            throw new IllegalArgumentException("최대 재시도 간격은 초기 재시도 간격 이상이어야 합니다.");
        }
        if (maxRetries < 1){
            throw new IllegalArgumentException("최대 재시도 횟수는 1 이상이어야 합니다.");
        }
    }

    private void validateDuration(Duration value, String fieldName){
        if (value == null) {
            throw new IllegalArgumentException(fieldName + "은 null일 수 없습니다.");
        }
        if (value.isNegative()) {
            throw new IllegalArgumentException(fieldName + "은 음수일 수 없습니다.");
        }
        if (value.isZero()) {
            throw new IllegalArgumentException(fieldName + "은 0일 수 없습니다.");
        }
        if (value.compareTo(Duration.ofMillis(1)) < 0) {
            throw new IllegalArgumentException(fieldName + "은 1밀리초 이상이어야 합니다.");
        }
    }
}
