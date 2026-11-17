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

/**
 * Redis에서 받은 JSON을 MessageDto로 복원하여 로컬 전달 서비스에 넘긴다.
 *
 * <p>손상된 JSON의 JacksonException은 기록 후 해당 메시지만 건너뛰고, JSON null도 전달하지 않는다.
 * 모든 런타임 예외를 삼키는 경계는 아니다. DB 저장·재발행을 하지 않아 수신으로 인한 중복 저장·발행 순환을 막는다.
 * MessageListener 등록은 RedisDirectMessageSubscription이 담당한다.
 */
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
