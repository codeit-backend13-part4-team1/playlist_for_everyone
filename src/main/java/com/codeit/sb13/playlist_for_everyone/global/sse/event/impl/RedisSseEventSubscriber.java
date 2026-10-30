package com.codeit.sb13.playlist_for_everyone.global.sse.event.impl;

import com.codeit.sb13.playlist_for_everyone.global.sse.event.SseEvent;
import com.codeit.sb13.playlist_for_everyone.global.sse.service.SseDeliveryService;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.stereotype.Component;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(
        prefix = "everyone.sse",
        name = "transport",
        havingValue = "redis"
)
public class RedisSseEventSubscriber implements MessageListener {

    private final ObjectMapper objectMapper;
    private final SseDeliveryService sseDeliveryService;

    @Override
    public void onMessage(Message message, byte @Nullable [] pattern) {
        sseDeliveryService.sendToLocalConnections(convertMessageToSseEvent(message));

    }

    private SseEvent<JsonNode> convertMessageToSseEvent(Message message) {
        return objectMapper.readValue(message.getBody(),
                new TypeReference<>() {});
    }
}
