package com.codeit.sb13.playlist_for_everyone.global.sse.event.impl;

import com.codeit.sb13.playlist_for_everyone.global.sse.event.SseEvent;
import com.codeit.sb13.playlist_for_everyone.global.sse.service.SseDeliveryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
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

    @Test
    @DisplayName("잘못된 JSON 메시지를 건너뛴 뒤 다음 정상 메시지는 로컬 전송 서비스에 전달한다.")
    void deliversValidEventAfterMalformedJson(){
        UUID eventId = UUID.randomUUID();
        UUID receiverId = UUID.randomUUID();

        String json = """
                {
                 "eventId": "%s",
                 "receiverId": "%s",
                 "eventName": "notifications",
                 "data": {"message": "다음 정상 알림"}
                }
                """.formatted(eventId, receiverId);

        Message invalidMessage = new DefaultMessage("test:sse:events".getBytes(StandardCharsets.UTF_8),
                "{invalid-json}".getBytes(StandardCharsets.UTF_8));

        Message validMessage = new DefaultMessage("test:sse:events".getBytes(StandardCharsets.UTF_8),
                json.getBytes(StandardCharsets.UTF_8));

        assertThatCode(()-> subscriber.onMessage(invalidMessage, null))
                .doesNotThrowAnyException();
        verifyNoInteractions(sseDeliveryService);
        subscriber.onMessage(validMessage, null);

        verify(sseDeliveryService)
                .sendToLocalConnections(eventCaptor.capture());

        SseEvent<?> received = eventCaptor.getValue();
        assertThat(received.eventId()).isEqualTo(eventId);
        assertThat(received.receiverId()).isEqualTo(receiverId);
    }

    @Test
    @DisplayName("eventId가 누락된 메시지는 예외를 전파하지 않고 로컬 전송 없이 건너뛴다.")
    void skipsMessageWithoutEventId(){
        UUID receiverId = UUID.randomUUID();

        String json = """
                {
                 "receiverId": "%s",
                 "eventName": "notifications",
                 "data": {"message": "테스트 알림"}
                }
                """.formatted( receiverId);

        Message message = new DefaultMessage("test:sse:events".getBytes(StandardCharsets.UTF_8),
                json.getBytes(StandardCharsets.UTF_8));

        assertThatCode(()-> subscriber.onMessage(message, null))
                .doesNotThrowAnyException();

        verifyNoInteractions(sseDeliveryService);

    }

    @Test
    @DisplayName("receiverId가 누락된 메시지는 예외를 전파하지 않고 로컬 전송 없이 건너뛴다.")
    void skipsMessageWithoutReceiverId(){
        UUID eventId = UUID.randomUUID();

        String json = """
                {
                 "eventId": "%s",
                 "eventName": "notifications",
                 "data": {"message": "테스트 알림"}
                }
                """.formatted( eventId);

        Message message = new DefaultMessage("test:sse:events".getBytes(StandardCharsets.UTF_8),
                json.getBytes(StandardCharsets.UTF_8));

        assertThatCode(()-> subscriber.onMessage(message, null))
                .doesNotThrowAnyException();

        verifyNoInteractions(sseDeliveryService);

    }

    @Test
    @DisplayName("eventName이 누락된 메시지는 예외를 전파하지 않고 로컬 전송 없이 건너뛴다.")
    void skipMessageWithoutEventName(){
        UUID eventId = UUID.randomUUID();
        UUID receiverId = UUID.randomUUID();

        String json = """
                {
                 "eventId": "%s",
                 "receiverId": "%s",
                 "data": {"message": "다음 정상 알림"}
                }
                """.formatted(eventId, receiverId);

        Message message = new DefaultMessage("test:sse:events".getBytes(StandardCharsets.UTF_8),
                json.getBytes(StandardCharsets.UTF_8));

        assertThatCode(()-> subscriber.onMessage(message, null))
                .doesNotThrowAnyException();

        verifyNoInteractions(sseDeliveryService);
    }

    @Test
    @DisplayName("eventId가 UUID 형식이 아니면 예외를 전파하지 않고 로컬 전송 없이 건너뛴다.")
    void skipsMessageWithInvalidEventId(){
        UUID receiverId = UUID.randomUUID();

        String json = """
                {
                 "eventId": "not-a-uuid",
                 "receiverId": "%s",
                 "eventName": "notifications",
                 "data": {"message": "다음 정상 알림"}
                }
                """.formatted(receiverId);

        Message message = new DefaultMessage("test:sse:events".getBytes(StandardCharsets.UTF_8),
                json.getBytes(StandardCharsets.UTF_8));

        assertThatCode(()-> subscriber.onMessage(message, null))
                .doesNotThrowAnyException();

        verifyNoInteractions(sseDeliveryService);
    }

    @Test
    @DisplayName("receiverId가 UUID 형식이 아니면 예외를 전파하지 않고 로컬 전송 없이 건너뛴다.")
    void skipsMessageWithInvalidReceiverId(){
        UUID eventId = UUID.randomUUID();

        String json = """
                {
                 "eventId": "%s",
                 "receiverId": "not-a-uuid",
                 "eventName": "notifications",
                 "data": {"message": "다음 정상 알림"}
                }
                """.formatted(eventId);

        Message message = new DefaultMessage("test:sse:events".getBytes(StandardCharsets.UTF_8),
                json.getBytes(StandardCharsets.UTF_8));

        assertThatCode(()-> subscriber.onMessage(message, null))
                .doesNotThrowAnyException();

        verifyNoInteractions(sseDeliveryService);
    }

    @ParameterizedTest(name = "[{index}] eventName=[{0}]")
    @ValueSource(strings = {"unknown-event", "", "   ", "NOTIFICATIONS"})
    @DisplayName("지원하지 않는 이벤트 이름이면 예외를 전파하지 않고 로컬 전송 없이 건너뛴다.")
    void skipsMessageWithUnsupportedEventName(String eventName){
        UUID eventId = UUID.randomUUID();
        UUID receiverId = UUID.randomUUID();

        String json = """
                {
                 "eventId": "%s",
                 "receiverId": "%s",
                 "eventName": "%s",
                 "data": {"message": "다음 정상 알림"}
                }
                """.formatted(eventId, receiverId, eventName);

        Message message = new DefaultMessage("test:sse:events".getBytes(StandardCharsets.UTF_8),
                json.getBytes(StandardCharsets.UTF_8));

        assertThatCode(()-> subscriber.onMessage(message, null))
                .doesNotThrowAnyException();

        verifyNoInteractions(sseDeliveryService);
    }

    @Test
    @DisplayName("direct-messages 이벤트를 수신하면 식별자와 이름 및 데이터를 보존해 로컬 전송한다.")
    void deliversDirectMessageEventToLocalConnections(){
        UUID eventId = UUID.randomUUID();
        UUID receiverId = UUID.randomUUID();

        String json = """
                {
                 "eventId": "%s",
                 "receiverId": "%s",
                 "eventName": "direct-messages",
                 "data": {"content": "테스트 DM"}
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
        assertThat(received.eventName()).isEqualTo("direct-messages");

        JsonNode receivedData = objectMapper.valueToTree(received.data());
        assertThat(receivedData)
                .isEqualTo(objectMapper.readTree("""
                        {"content": "테스트 DM"}
                        """));
    }


    @Test
    @DisplayName("data가 null인 이벤트도 식별자와 이름을 보존해 로컬 전송한다.")
    void deliversEventWithNullData(){
        UUID eventId = UUID.randomUUID();
        UUID receiverId = UUID.randomUUID();

        String json = """
                {
                 "eventId": "%s",
                 "receiverId": "%s",
                 "eventName": "notifications",
                 "data": null
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
        assertThat(receivedData.isNull()).isTrue();
    }

}