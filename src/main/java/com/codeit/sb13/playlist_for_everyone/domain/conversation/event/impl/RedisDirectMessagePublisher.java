package com.codeit.sb13.playlist_for_everyone.domain.conversation.event.impl;

import com.codeit.sb13.playlist_for_everyone.domain.conversation.config.DirectMessageTransportProperties;
import com.codeit.sb13.playlist_for_everyone.domain.conversation.dto.response.MessageDto;
import com.codeit.sb13.playlist_for_everyone.domain.conversation.event.DirectMessagePublisher;
import com.codeit.sb13.playlist_for_everyone.global.exception.conversation.DirectMessagePublishException;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

/**
 * DM 응답을 JSON으로 직렬화하여 설정된 Redis Pub/Sub 채널에 한 번 발행한다.
 *
 * <p>발행 서버 자신도 Subscriber를 통해 로컬 전달하므로 여기서 WebSocket 전송을 추가하지 않는다.
 * 직렬화·Redis 접근 실패는 저장 완료 정보가 담긴 예외로 전달한다. 자동 재시도나 이력 저장은 하지 않는다.
 * Pub/Sub 반환값은 수신 클라이언트의 처리 성공 건수가 아니므로 성공 응답의 근거로 사용하지 않는다.
 */
@Component
@ConditionalOnProperty(
        prefix = "everyone.dm.websocket",
        name = "transport",
        havingValue = "redis"
)
@RequiredArgsConstructor
public class RedisDirectMessagePublisher implements DirectMessagePublisher {

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final DirectMessageTransportProperties properties;

    @Override
    public void publish(MessageDto message) {
       try {
           String json = objectMapper.writeValueAsString(message);
           redisTemplate.convertAndSend(properties.redis().channel(), json);
       } catch (JacksonException | DataAccessException e){
           throw new DirectMessagePublishException(message.id(), message.conversationId(), e);
       }
    }
}
