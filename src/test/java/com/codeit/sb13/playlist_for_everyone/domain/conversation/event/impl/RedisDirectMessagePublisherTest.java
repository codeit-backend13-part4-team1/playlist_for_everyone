package com.codeit.sb13.playlist_for_everyone.domain.conversation.event.impl;

import com.codeit.sb13.playlist_for_everyone.domain.conversation.config.DirectMessageTransportProperties;
import com.codeit.sb13.playlist_for_everyone.domain.conversation.dto.response.MessageDto;
import com.codeit.sb13.playlist_for_everyone.domain.conversation.event.DirectMessagePublisher;
import com.codeit.sb13.playlist_for_everyone.domain.user.dto.response.UserSummary;
import com.codeit.sb13.playlist_for_everyone.global.exception.conversation.DirectMessagePublishException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("Redis DM 메시지 발행")
class RedisDirectMessagePublisherTest {

    private static final String CHANNEL = "test:dm:events";

    @Mock
    private StringRedisTemplate redisTemplate;
    private ObjectMapper objectMapper;
    private DirectMessageTransportProperties properties;
    private DirectMessagePublisher publisher;

    @BeforeEach
    void setUp(){
        objectMapper = JsonMapper.builder().build();
        properties = new DirectMessageTransportProperties(
                "redis",
                new DirectMessageTransportProperties.Redis(CHANNEL)
        );
        publisher = new RedisDirectMessagePublisher(redisTemplate, objectMapper, properties);
    }

    @Test
    @DisplayName("메시지의 모든 정보를 JSON으로 변환해 설정된 Redis 채널에 한 번 발행한다.")
    void publish_sendsMessageAsJsonToConfiguredChannel(){
        MessageDto message = createMessage();

        when(redisTemplate.convertAndSend(eq(CHANNEL), anyString())).thenReturn(1L);
        publisher.publish(message);

        ArgumentCaptor<String> jsonCaptor = ArgumentCaptor.forClass(String.class);
        verify(redisTemplate).convertAndSend(eq(CHANNEL), jsonCaptor.capture());
        JsonNode publishedJson = objectMapper.readTree(jsonCaptor.getValue());
        assertThat(publishedJson).isEqualTo(objectMapper.valueToTree(message));
        verifyNoMoreInteractions(redisTemplate);
    }

    @Test
    @DisplayName("JSON 직렬화에 실패하면 원인과 저장 정보를 담은 예외를 전달하고 Redis를 호출하지않는다.")
    void publish_reportsSerializationFailureWithoutPublishing(){
        MessageDto message = createMessage();

        ObjectMapper failingMapper = mock(ObjectMapper.class);
        JacksonException cause = mock(JacksonException.class);

        when(failingMapper.writeValueAsString(message)).thenThrow(cause);
        DirectMessagePublisher failingPublisher = new RedisDirectMessagePublisher(redisTemplate, failingMapper, properties);

        assertThatThrownBy(() -> failingPublisher.publish(message))
                .isInstanceOfSatisfying(DirectMessagePublishException.class,
                        e ->{
                    assertThat(e.getCause()).isSameAs(cause);
                    assertThat(e.getDetails())
                            .containsEntry("directMessageId", message.id())
                            .containsEntry("conversationId", message.conversationId())
                            .containsEntry("persisted", true);
                });

        verify(failingMapper).writeValueAsString(message);
        verifyNoInteractions(redisTemplate);
    }

    @Test
    @DisplayName("Redis 발행에 실패하면 원인과 저장정보를 담은 예외를 전달하고 재시도하지 않는다.")
    void publish_reportsRedisFailureWithoutRetrying(){
        MessageDto message = createMessage();
        RedisConnectionFailureException cause = new RedisConnectionFailureException("테스트용 Redis 연결 실패");

        when(redisTemplate.convertAndSend(eq(CHANNEL), anyString())).thenThrow(cause);
        assertThatThrownBy(() -> publisher.publish(message))
                .isInstanceOfSatisfying(DirectMessagePublishException.class,
                        e -> {
                    assertThat(e.getCause()).isSameAs(cause);
                    assertThat(e.getDetails())
                            .containsEntry("directMessageId", message.id())
                            .containsEntry("conversationId", message.conversationId())
                            .containsEntry("persisted", true);
                });

        verify(redisTemplate).convertAndSend(eq(CHANNEL), anyString());
        verifyNoMoreInteractions(redisTemplate);
    }

    @Test
    @DisplayName("Redis 구독자 수가 0이어도 예외 없이 발행을 완료하고 재시도하지 않는다.")
    void publish_completesWhenNoSubscribersExist(){
        MessageDto message = createMessage();

        when(redisTemplate.convertAndSend(eq(CHANNEL),anyString())).thenReturn(0L);
        assertThatCode(() -> publisher.publish(message)).doesNotThrowAnyException();

        verify(redisTemplate).convertAndSend(eq(CHANNEL),anyString());
        verifyNoMoreInteractions(redisTemplate);
    }

    private MessageDto createMessage() {
        return new MessageDto(
                UUID.randomUUID(),
                UUID.randomUUID(),
                LocalDateTime.of(2026, 11, 16, 10, 20, 30),
                new UserSummary(UUID.randomUUID(), "발신자", "https://example.com/profile.png"),
                new UserSummary(UUID.randomUUID(), "수신자", null),
                "첫 번째 줄 \n\"테스트\" 메시지"
        );
    }


}
