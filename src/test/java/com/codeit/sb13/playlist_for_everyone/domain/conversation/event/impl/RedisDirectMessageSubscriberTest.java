package com.codeit.sb13.playlist_for_everyone.domain.conversation.event.impl;

import com.codeit.sb13.playlist_for_everyone.domain.conversation.dto.response.MessageDto;
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
import org.springframework.messaging.MessageDeliveryException;
import org.springframework.messaging.simp.SimpMessagingTemplate;
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
    private SimpMessagingTemplate messagingTemplate;

    private ObjectMapper objectMapper;
    private MessageListener subscriber;

    @BeforeEach
    void setUp(){
        objectMapper = JsonMapper.builder().build();
        subscriber = new RedisDirectMessageSubscriber(objectMapper, messagingTemplate);
    }

    @Test
    @DisplayName("정상 Redis DM을 수신하면 메시지 정보를 보존해 해당 대화방 경로로 한 번 전달한다.")
    void onMessage_deliversMessageToConversationDestination(){
        MessageDto message = createMessage();
        Message redisMessage = toRedisMessage(message);

        subscriber.onMessage(redisMessage, null);
        verify(messagingTemplate).convertAndSend(
                "/sub/conversations/"+message.conversationId()
        + "/direct-messages",message);

        verifyNoMoreInteractions(messagingTemplate);
    }

    @Test
    @DisplayName("잘못된 JSON은 전송 없이 건너뛰고 다음 정상 DM은 해당 대화방으로 전달한다.")
    void onMessage_deliversValidMessageAfterMalformedJson(){
        Message invalidMessage = new DefaultMessage(
                CHANNEL.getBytes(StandardCharsets.UTF_8),
                "{invalid-json}".getBytes(StandardCharsets.UTF_8)
        );

        MessageDto message = createMessage();
        Message validMessage = toRedisMessage(message);

        assertThatCode(() -> subscriber.onMessage(invalidMessage, null))
                .doesNotThrowAnyException();

        verifyNoInteractions(messagingTemplate);
        subscriber.onMessage(validMessage, null);
        verify(messagingTemplate).convertAndSend(
                "/sub/conversations/"+message.conversationId()
        + "/direct-messages",message);
        verifyNoMoreInteractions(messagingTemplate);
    }

    @Test
    @DisplayName("WebSocket 전송에 실패한 DM은 재시도하지 않고 같은 대화방의 다음 DM을 전달한다.")
    void onMessage_deliversNextMessageAfterDeliveryFailureWithoutRetrying(){
        MessageDto failedMessage = createMessage();
        MessageDto nextMessage = new MessageDto(UUID.randomUUID(),
                failedMessage.conversationId(),
                failedMessage.createdAt().plusSeconds(1),
                failedMessage.sender(),
                failedMessage.receiver(),
                "다음 정상 메시지"
        );

        Message failedRedisMessage = toRedisMessage(failedMessage);
        Message nextRedisMessage = toRedisMessage(nextMessage);

        String destination = "/sub/conversations/" + failedMessage.conversationId()
                + "/direct-messages";

        doThrow(new MessageDeliveryException("테스트용 WebSocket 전송 실패"))
                .when(messagingTemplate)
                .convertAndSend(destination, failedMessage);

        assertThatCode(()-> subscriber.onMessage(failedRedisMessage, null))
                .doesNotThrowAnyException();
        assertThatCode(() -> subscriber.onMessage(nextRedisMessage, null))
                .doesNotThrowAnyException();

        verify(messagingTemplate).convertAndSend(destination, failedMessage);
        verify(messagingTemplate).convertAndSend(destination, nextMessage);
        verifyNoMoreInteractions(messagingTemplate);
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