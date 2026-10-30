package com.codeit.sb13.playlist_for_everyone.global.sse.event.impl;

import com.codeit.sb13.playlist_for_everyone.global.sse.event.SseEvent;
import com.codeit.sb13.playlist_for_everyone.global.sse.service.SseDeliveryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.connection.DefaultMessage;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
@DisplayName("Redis SSE 이벤트 수신")
class RedisSseEventSubscriberTest {

    @Mock
    private SseDeliveryService sseDeliveryService;

    @Captor
    private ArgumentCaptor<SseEvent<?>> eventCaptor;

    private ObjectMapper objectMapper;
    private MessageListener subscriber;

    @BeforeEach
    void setUp(){
        objectMapper = JsonMapper.builder().build();
        subscriber = new RedisSseEventSubscriber(objectMapper, sseDeliveryService);
    }

    @Test
    @DisplayName("정상 Redis 메시지를 수신하면 식별자와 이름 및 데이터를 보존해 로컬 전송 서비스에 전달한다.")
    void deliversReceivedEventToLocalConnections(){
        UUID eventId = UUID.randomUUID();
        UUID receiverId = UUID.randomUUID();

        String json = """
                {
                 "eventId": "%s",
                 "receiverId": "%s",
                 "eventName": "notifications",
                 "data": {"message": "테스트 알림"}
                }
                """.formatted(eventId, receiverId);

        Message message = new DefaultMessage("test:sse:events".getBytes(StandardCharsets.UTF_8),
                json.getBytes(StandardCharsets.UTF_8));

        subscriber.onMessage(message,  null);
        verify(sseDeliveryService)
                .sendToLocalConnections(eventCaptor.capture());

        SseEvent<?> received = eventCaptor.getValue();

        assertThat(received.eventId()).isEqualTo(eventId);
        assertThat(received.receiverId()).isEqualTo(receiverId);
        assertThat(received.eventName()).isEqualTo("notifications");

        JsonNode receivedData = objectMapper.valueToTree(received.data());
        assertThat(receivedData)
                .isEqualTo(objectMapper.readTree("""
                        {"message": "테스트 알림"}
                        """));
    }

    @Test
    @DisplayName("잘못된 JSON 메시지는 예외를 전파하지 않고 로컬 전송 없이 건너뛴다.")
    void skipMalformedJsonWithoutDelivery(){
        Message message = new DefaultMessage("test:sse:events".getBytes(StandardCharsets.UTF_8),
                "{invalid-json}".getBytes(StandardCharsets.UTF_8));

        assertThatCode(()-> subscriber.onMessage(message, null))
                .doesNotThrowAnyException();

        verifyNoInteractions(sseDeliveryService);
    }

}