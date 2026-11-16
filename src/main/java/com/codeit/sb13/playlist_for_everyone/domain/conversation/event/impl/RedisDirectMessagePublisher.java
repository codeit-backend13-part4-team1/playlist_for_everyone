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
