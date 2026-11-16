package com.codeit.sb13.playlist_for_everyone.domain.conversation.event.impl;

import com.codeit.sb13.playlist_for_everyone.domain.conversation.dto.response.MessageDto;
import com.codeit.sb13.playlist_for_everyone.domain.conversation.service.DirectMessageDeliveryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.util.Optional;

@Component
@ConditionalOnProperty(
        prefix = "everyone.dm.websocket",
        name = "transport",
        havingValue = "redis"
)
@RequiredArgsConstructor
@Slf4j
public class RedisDirectMessageSubscriber implements MessageListener {

    private final ObjectMapper objectMapper;
    private final DirectMessageDeliveryService deliveryService;

    @Override
    public void onMessage(Message message, byte @Nullable [] pattern) {
        parseMessageDto(message)
                .ifPresent(deliveryService::sendToLocalSubscribers);
    }

    private Optional<MessageDto> parseMessageDto(Message message) {
        try {
            return Optional.ofNullable(objectMapper.readValue(message.getBody(), MessageDto.class));
        }catch (JacksonException e){
            log.warn("Redis DM 메시지 변환에 실패하여 건너뜁니다. exceptionType={}",
                    e.getClass().getSimpleName());
            return Optional.empty();
        }
    }
}
