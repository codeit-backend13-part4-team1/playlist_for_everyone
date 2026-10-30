package com.codeit.sb13.playlist_for_everyone.global.sse.event.impl;

import com.codeit.sb13.playlist_for_everyone.global.exception.sse.SsePublishException;
import com.codeit.sb13.playlist_for_everyone.global.sse.config.SseTransportProperties;
import com.codeit.sb13.playlist_for_everyone.global.sse.event.SseEvent;
import com.codeit.sb13.playlist_for_everyone.global.sse.event.SseEventPublisher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(
        prefix = "everyone.sse",
        name = "transport",
        havingValue = "redis"
)
@Slf4j
public class RedisSseEventPublisher implements SseEventPublisher {

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final SseTransportProperties properties;

    @Override
    public void publish(SseEvent<?> sseEvent) {
        publishEvent(sseEvent);
    }

    private void publishEvent(SseEvent<?> sseEvent) {
        try {
            redisTemplate.convertAndSend(
                    properties.redis().channel(),
                    toJsonString(sseEvent)
            );
        } catch (DataAccessException e){
            throw new SsePublishException(e);
        }
    }

    private String toJsonString(SseEvent<?> sseEvent) {
        try {
            return objectMapper.writeValueAsString(sseEvent);
        }catch (JacksonException e){
            throw new SsePublishException(e);
        }
    }
}
