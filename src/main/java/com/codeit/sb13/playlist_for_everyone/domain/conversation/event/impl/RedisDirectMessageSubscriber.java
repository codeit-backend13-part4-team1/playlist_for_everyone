package com.codeit.sb13.playlist_for_everyone.domain.conversation.event.impl;

import com.codeit.sb13.playlist_for_everyone.domain.conversation.dto.response.MessageDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.messaging.MessagingException;
import org.springframework.messaging.simp.SimpMessagingTemplate;
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
    private final SimpMessagingTemplate messagingTemplate;

    @Override
    public void onMessage(Message message, byte @Nullable [] pattern) {
        parseMessageDto(message)
                .ifPresent(this::publishDirectMessage);
    }

    private void publishDirectMessage(MessageDto directMessage) {
        String destination = "/sub/conversations/"+ directMessage.conversationId() + "/direct-messages";
        try {
            messagingTemplate.convertAndSend(destination, directMessage);
        }catch (MessagingException e ){
            log.warn(
                    "DM WebSocket 전송에 실패했습니다. directMessageId={}, conversationId={}, exceptionType={}",
                    directMessage.id(),
                    directMessage.conversationId(),
                    e.getClass().getSimpleName()
            );
        }
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
