package com.codeit.sb13.playlist_for_everyone.global.sse.event.impl;

import com.codeit.sb13.playlist_for_everyone.global.sse.event.SseEvent;
import com.codeit.sb13.playlist_for_everyone.global.sse.service.SseDeliveryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.Optional;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(
        prefix = "everyone.sse",
        name = "transport",
        havingValue = "redis"
)
@Slf4j
public class RedisSseEventSubscriber implements MessageListener {

    private final ObjectMapper objectMapper;
    private final SseDeliveryService sseDeliveryService;

    @Override
    public void onMessage(Message message, byte @Nullable [] pattern) {
       convertMessageToSseEvent(message)
               .ifPresent(sseDeliveryService::sendToLocalConnections);
    }

    private Optional<SseEvent<JsonNode>> convertMessageToSseEvent(Message message) {
        try {
            return objectMapper.readValue(message.getBody(),
                    new TypeReference<>() {});
        }catch (JacksonException e){
            log.warn("SSE Redis 메시지 변환에 실패하여 전송을 건너뜁니다. exceptionType={}", e.getClass().getSimpleName());
            return Optional.empty();
        }
    }
}
