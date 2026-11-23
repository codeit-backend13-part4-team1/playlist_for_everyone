package com.codeit.sb13.playlist_for_everyone.global.sse.event.impl;

import com.codeit.sb13.playlist_for_everyone.global.sse.config.SseTransportProperties;
import com.codeit.sb13.playlist_for_everyone.global.sse.event.RevokeEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Recover;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@Component
@RequiredArgsConstructor
@Slf4j
public class RedisRevokeEventPublisher {

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final SseTransportProperties properties;

    @Retryable(
            retryFor = {
                    RedisConnectionFailureException.class,
                    QueryTimeoutException.class
            },
            maxAttempts = 3,
            backoff = @Backoff(delay = 1000, multiplier = 2)
    )
    public void publish(RevokeEvent<?> event) {
        try {
            String payload = objectMapper.writeValueAsString(event);
            redisTemplate.convertAndSend(properties.redis().revokeChannel(), payload);
        } catch (JacksonException e) {
            log.warn("SSE 세션 폐기 이벤트 발행에 실패했습니다. userId={}, exceptionType={}",
                    event.userId(), e.getClass().getSimpleName());
        }
    }

    @Recover
    public void recover(DataAccessException failure, RevokeEvent<?> event){
        log.warn("SSE 세션 폐기 이벤트 Redis 발행에 최종 실패했습니다. userId={}, exceptionType={}",
                event.userId(), failure.getClass().getSimpleName());
    }

}
