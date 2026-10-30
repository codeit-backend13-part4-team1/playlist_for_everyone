package com.codeit.sb13.playlist_for_everyone.global.sse.event.impl;

import com.codeit.sb13.playlist_for_everyone.global.sse.config.SseTransportProperties;
import com.codeit.sb13.playlist_for_everyone.global.sse.event.Event;
import com.codeit.sb13.playlist_for_everyone.global.sse.event.SseEvent;
import com.codeit.sb13.playlist_for_everyone.global.sse.event.SseEventPublisher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("Redis SSE 이벤트 발행")
class RedisSseEventPublisherTest {

    @Mock
    private StringRedisTemplate redisTemplate;

    private ObjectMapper objectMapper;
    private SseEventPublisher publisher;

    @BeforeEach
    void setUp(){
        objectMapper = JsonMapper.builder().build();
        SseTransportProperties properties = new SseTransportProperties("redis",
                new SseTransportProperties.Redis("test:sse:events")
        );

        publisher = new RedisSseEventPublisher(redisTemplate, objectMapper, properties);
    }

    @Test
    @DisplayName("SSE 이벤트의 식별자와 이름 및 데이터를 JSON으로 변환해 설정된 Redis 채널에 발행한다.")
    void publishesEventAsJsonToConfiguredChannel(){
        UUID eventId = UUID.randomUUID();
        UUID receiverId = UUID.randomUUID();

        SseEvent<Map<String, String>> event = new SseEvent<>(
                eventId,
                receiverId,
                Event.NOTIFICATIONS.getName(),
                Map.of("message", "테스트 알림")
        );

        publisher.publish(event);

        ArgumentCaptor<String> messageCaptor = ArgumentCaptor.forClass(String.class);
        verify(redisTemplate)
                .convertAndSend(eq("test:sse:events"), messageCaptor.capture());

        JsonNode message = objectMapper.readTree(messageCaptor.getValue());

        assertThat(message.path("eventId").asString())
                .isEqualTo(eventId.toString());

        assertThat(message.path("receiverId").asString())
                .isEqualTo(receiverId.toString());

        assertThat(message.path("eventName").asString())
                .isEqualTo(Event.NOTIFICATIONS.getName());

        assertThat(message.path("data").path("message").asString())
                .isEqualTo("테스트 알림");

    }
}