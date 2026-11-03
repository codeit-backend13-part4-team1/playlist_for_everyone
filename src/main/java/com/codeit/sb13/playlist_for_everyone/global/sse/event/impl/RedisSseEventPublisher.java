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

/**
 * 공통 이벤트 전체를 JSON으로 변환해 설정된 Redis 채널에 발행한다.
 * StringRedisTemplate으로 JSON 문자열을 보내므로 다른 도메인의 객체 직렬화 설정을 사용하지 않는다.
 *
 * <p>발행 서버도 구독 리스너를 거쳐 전송한다. 여기서 로컬 전송을 함께 호출하면 중복 전달될 수 있다.
 * Redis의 반환값은 구독자 수이므로 브라우저 수신 확인으로 해석하지 않는다.
 *
 * <p>직렬화·Redis 발행 실패는 식별자와 예외 타입만 로그에 남기고 SsePublishException으로 전달한다.
 * 이 로그에는 data나 예외 메시지를 넣지 않으며, 원인 예외는 호출자가 확인할 수 있도록 보존한다.
 * 자동 재시도와 이벤트 이력 저장은 수행하지 않는다.
 */
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
            log.warn("SSE Redis 발행에 실패했습니다. eventId={}, receiverId={}, exceptionType={}",
                    sseEvent.eventId(), sseEvent.receiverId(), e.getClass().getSimpleName());
            throw new SsePublishException(e);
        }
    }

    private String toJsonString(SseEvent<?> sseEvent) {
        try {
            return objectMapper.writeValueAsString(sseEvent);
        }catch (JacksonException e){
            log.warn("SSE 이벤트 JSON 직렬화에 실패했습니다. eventId={}, receiverId={}, exceptionType={}",
                    sseEvent.eventId(), sseEvent.receiverId(), e.getClass().getSimpleName());
            throw new SsePublishException(e);
        }
    }
}
