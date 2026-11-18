package com.codeit.sb13.playlist_for_everyone.domain.conversation.event.impl;

import com.codeit.sb13.playlist_for_everyone.domain.conversation.dto.response.MessageDto;
import com.codeit.sb13.playlist_for_everyone.domain.conversation.service.DirectMessageDeliveryService;
import com.codeit.sb13.playlist_for_everyone.domain.user.dto.response.UserSummary;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.connection.DefaultMessage;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("Redis DM 메시지 수신")
class RedisDirectMessageSubscriberTest {

    private static final String CHANNEL = "test:dm:events";

    @Mock
    private DirectMessageDeliveryService deliveryService;

    private ObjectMapper objectMapper;
    private MessageListener subscriber;

    @BeforeEach
    void setUp(){
        objectMapper = JsonMapper.builder().build();
        subscriber = new RedisDirectMessageSubscriber(objectMapper, deliveryService);
    }

    @Test
    @DisplayName("정상 Redis DM을 수신하면 메시지 정보를 보존해 전달 서비스에 한 번 위임한다.")
    void onMessage_delegatesDeserializedMessageToDeliveryService(){
        MessageDto message = createMessage();
        Message redisMessage = toRedisMessage(message);

        subscriber.onMessage(redisMessage, null);
        verify(deliveryService).sendToLocalSubscribers(message);

        verifyNoMoreInteractions(deliveryService);
    }

    @Test
    @DisplayName("잘못된 JSON은 서비스 호출 없이 건너뛰고 다음 정상 DM은 전달 서비스에 한 번 위임한다.")
    void onMessage_delegatesValidMessageAfterMalformedJson(){
        Message invalidMessage = new DefaultMessage(
                CHANNEL.getBytes(StandardCharsets.UTF_8),
                "{invalid-json}".getBytes(StandardCharsets.UTF_8)
        );

        MessageDto message = createMessage();
        Message validMessage = toRedisMessage(message);

        assertThatCode(() -> subscriber.onMessage(invalidMessage, null))
                .doesNotThrowAnyException();

        verifyNoInteractions(deliveryService);
        subscriber.onMessage(validMessage, null);
        verify(deliveryService).sendToLocalSubscribers(message);
        verifyNoMoreInteractions(deliveryService);
    }

    private MessageDto createMessage() {
        return new MessageDto(
                UUID.randomUUID(),
                UUID.randomUUID(),
                LocalDateTime.of(2026, 11, 16,
                        10, 20, 30),
                new UserSummary(
                        UUID.randomUUID(),
                        "발신자",
                        "https://example.com/profile.png"
                ),
                new UserSummary(UUID.randomUUID(), "수신자", null),
                "첫 번째 줄\n\"테스트\" 메시지"
        );
    }

    private Message toRedisMessage(MessageDto message) {
        return new DefaultMessage(
                CHANNEL.getBytes(StandardCharsets.UTF_8),
                objectMapper.writeValueAsBytes(message)
        );
    }

}
