package com.codeit.sb13.playlist_for_everyone.domain.conversation.service;

import com.codeit.sb13.playlist_for_everyone.domain.conversation.mapper.DirectMessageErrorMapper;
import com.codeit.sb13.playlist_for_everyone.domain.conversation.service.impl.DirectMessageErrorSenderImpl;
import com.codeit.sb13.playlist_for_everyone.global.dto.response.ApiErrorResponse;
import com.codeit.sb13.playlist_for_everyone.global.exception.ApiErrorCode;
import com.codeit.sb13.playlist_for_everyone.global.exception.conversation.DirectMessagePublishException;
import com.codeit.sb13.playlist_for_everyone.global.websocket.entity.WSSession;
import com.codeit.sb13.playlist_for_everyone.global.websocket.registry.session.WebSocketSessionRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageDeliveryException;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.util.MimeTypeUtils;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = {
        DirectMessageErrorSenderImpl.class,
        DirectMessageErrorMapper.class,
        DirectMessageErrorSenderTest.TestConfig.class
})
@DisplayName("DM WebSocket 오류 전송")
class DirectMessageErrorSenderTest {

    @Autowired
    private DirectMessageErrorSender errorSender;

    @MockitoSpyBean
    private ObjectMapper objectMapper;

    @MockitoBean(name = "clientOutboundChannel")
    private MessageChannel clientOutboundChannel;

    @MockitoBean
    private WebSocketSessionRegistry webSocketSessionRegistry;

    @Test
    @DisplayName("지정한 WebSocket 세션으로 ERROR 프레임을 한 번 전달한다.")
    void sendToSession_sendsErrorFrameToRequestedSession() {
        String sessionId = "requester-session";
        BadCredentialsException exception =
                new BadCredentialsException("테스트용 인증 실패");

        when(clientOutboundChannel.send(any())).thenReturn(true);

        errorSender.sendToSession(sessionId, exception);

        ArgumentCaptor<Message<?>> captor = ArgumentCaptor.captor();
        verify(clientOutboundChannel).send(captor.capture());

        StompHeaderAccessor headers =
                StompHeaderAccessor.wrap(captor.getValue());

        assertThat(headers.getCommand()).isEqualTo(StompCommand.ERROR);
        assertThat(headers.getSessionId()).isEqualTo(sessionId);
        verifyNoMoreInteractions(clientOutboundChannel);
        verifyNoInteractions(webSocketSessionRegistry);
    }

    @Test
    @DisplayName("오류 응답을 JSON 본문으로 전달하며 저장된 메시지 정보를 유지한다.")
    void sendToSession_serializesErrorResponseAsJson() {
        UUID directMessageId = UUID.randomUUID();
        UUID conversationId = UUID.randomUUID();

        DirectMessagePublishException exception =
                new DirectMessagePublishException(
                        directMessageId,
                        conversationId,
                        new IllegalStateException("내부 Redis 발행 실패")
                );

        when(clientOutboundChannel.send(any())).thenReturn(true);

        errorSender.sendToSession("requester-session", exception);

        ArgumentCaptor<Message<?>> captor = ArgumentCaptor.captor();
        verify(clientOutboundChannel).send(captor.capture());

        Message<?> message = captor.getValue();
        StompHeaderAccessor headers = StompHeaderAccessor.wrap(message);

        assertThat(headers.getContentType())
                .isEqualTo(MimeTypeUtils.APPLICATION_JSON);
        assertThat(message.getPayload()).isInstanceOf(byte[].class);

        JsonNode actualBody =
                objectMapper.readTree((byte[]) message.getPayload());

        ApiErrorResponse expectedResponse = new ApiErrorResponse(
                DirectMessagePublishException.class.getSimpleName(),
                ApiErrorCode.DIRECT_MESSAGE_PUBLISH_FAILED.getMessage(),
                Map.of(
                        "directMessageId", directMessageId,
                        "conversationId", conversationId,
                        "persisted", true
                )
        );

        assertThat(actualBody)
                .isEqualTo(objectMapper.valueToTree(expectedResponse));

        verifyNoMoreInteractions(clientOutboundChannel);
    }

    @Test
    @DisplayName("오류 응답 직렬화에 실패하면 전송하지 않고 해당 세션만 종료한다.")
    void sendToSession_closesSessionWhenSerializationFails() throws IOException {
        String sessionId = "requester-session";
        BadCredentialsException exception =
                new BadCredentialsException("테스트용 인증 실패");
        WSSession session = mock(WSSession.class);

        when(webSocketSessionRegistry.query(sessionId)).thenReturn(Optional.of(session));

        JacksonException serializationFailure = mock(JacksonException.class);

        doThrow(serializationFailure)
                .when(objectMapper)
                .writeValueAsBytes(any(ApiErrorResponse.class));

        assertThatCode(() -> errorSender.sendToSession(sessionId, exception))
                .doesNotThrowAnyException();

        verifyNoInteractions(clientOutboundChannel);
        verify(webSocketSessionRegistry).query(sessionId);
        verify(session).disconnect();
        verifyNoMoreInteractions(webSocketSessionRegistry, session);
    }

    @Test
    @DisplayName("오류 프레임 전송 결과가 false이면 재시도 없이 해당 세션만 종료한다.")
    void sendToSession_closesSessionWhenChannelReturnsFalse() throws IOException {
        String sessionId = "requester-session";
        BadCredentialsException exception =
                new BadCredentialsException("테스트용 인증 실패");
        WSSession session = mock(WSSession.class);

        when(webSocketSessionRegistry.query(sessionId)).thenReturn(Optional.of(session));
        when(clientOutboundChannel.send(any())).thenReturn(false);

        assertThatCode(() -> errorSender.sendToSession(sessionId, exception))
                .doesNotThrowAnyException();

        verify(clientOutboundChannel).send(any());
        verify(webSocketSessionRegistry).query(sessionId);
        verify(session).disconnect();
        verifyNoMoreInteractions(clientOutboundChannel, webSocketSessionRegistry, session);
    }

    @Test
    @DisplayName("오류 프레임 전송 중 예외가 발생하면 재시도 없이 해당 세션만 종료한다.")
    void sendToSession_closesSessionWhenChannelThrowsException() throws IOException {
        String sessionId = "requester-session";
        BadCredentialsException exception =
                new BadCredentialsException("테스트용 인증 실패");
        WSSession session = mock(WSSession.class);

        MessageDeliveryException deliveryFailure =
                new MessageDeliveryException("테스트용 오류 프레임 전송 실패");

        when(webSocketSessionRegistry.query(sessionId)).thenReturn(Optional.of(session));
        when(clientOutboundChannel.send(any())).thenThrow(deliveryFailure);

        assertThatCode(() -> errorSender.sendToSession(sessionId, exception))
                .doesNotThrowAnyException();

        verify(clientOutboundChannel).send(any());
        verify(webSocketSessionRegistry).query(sessionId);
        verify(session).disconnect();
        verifyNoMoreInteractions(clientOutboundChannel, webSocketSessionRegistry, session);
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class TestConfig {

        @Bean
        ObjectMapper objectMapper() {
            return JsonMapper.builder().build();
        }
    }
}
