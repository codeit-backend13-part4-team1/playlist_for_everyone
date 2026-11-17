package com.codeit.sb13.playlist_for_everyone.global.websocket.error;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageDeliveryException;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompConversionException;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.web.socket.messaging.SubProtocolErrorHandler;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.*;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = WebSocketStompErrorHandler.class)
@DisplayName("공통 STOMP 인바운드 오류 처리")
class WebSocketStompErrorHandlerTest {

    @Autowired
    private SubProtocolErrorHandler<byte[]> errorHandler;

    @MockitoBean
    private WebSocketErrorSender errorSender;

    @Test
    @DisplayName("DM SEND 오류를 요청한 세션의 전송기에 위임하고 기본 오류 응답을 중복 생성하지 않는다.")
    void handleClientMessageProcessingError_forwardsDirectMessageSendFailure(){
        String sessionId = "dm-test-session";
        StompHeaderAccessor headers = StompHeaderAccessor.create(StompCommand.SEND);

        headers.setSessionId(sessionId);
        headers.setDestination("/pub/conversations/" + UUID.randomUUID() + "/direct-messages");
        Message<byte[]> message = MessageBuilder.createMessage(
                new byte[0],
                headers.getMessageHeaders()
        );

        MessageDeliveryException exception = new MessageDeliveryException(message, new AccessDeniedException("테스트용 대화방 접근 거부"));


        Message<byte[]> result = errorHandler.handleClientMessageProcessingError(message, exception);
        verify(errorSender).sendToSession(eq(sessionId), same(exception));
        verifyNoMoreInteractions(errorSender);
        assertThat(result).isNull();
    }

    @Test
    @DisplayName("DM SUBSCRIBE 오류를 요청한 세션의 전송기에 위임하고 기본 오류 응답을 중복 생성하지 않는다.")
    void handleClientMessageProcessingError_forwardsDirectMessageSubscribeFailure(){
        String sessionId = "dm-subscribe-session";
        StompHeaderAccessor headers = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
        headers.setSessionId(sessionId);
        headers.setSubscriptionId("dm-subscription");
        headers.setDestination("/sub/conversations/" + UUID.randomUUID() + "/direct-messages");
        Message<byte[]> message = MessageBuilder.createMessage(
                new byte[0],
                headers.getMessageHeaders()
        );

        MessageDeliveryException exception = new MessageDeliveryException(message, new AccessDeniedException("테스트용 대화방 구독 거부"));
        Message<byte[]> result = errorHandler.handleClientMessageProcessingError(message, exception);
        verify(errorSender).sendToSession(eq(sessionId), same(exception));
        verifyNoMoreInteractions(errorSender);
        assertThat(result).isNull();
    }

    @ParameterizedTest(name = "[{index}] command={0}, destination={1}")
    @CsvSource({
            "SUBSCRIBE, /sub/conversations/not-a-uuid/direct-messages",
            "SUBSCRIBE, /sub/conversations/*/direct-messages",
            "SEND, /sub/conversations/00000000-0000-0000-0000-000000000001/direct-messages",
            "SUBSCRIBE, /pub/conversations/00000000-0000-0000-0000-000000000001/direct-messages"
    })
    @DisplayName("거절된 DM 경로의 오류도 요청한 세션의 전송기에 위임한다.")
    void handleClientMessageProcessingError_forwardsRejectedDirectMessagePaths(
            StompCommand command, String destination
    ){
        String sessionId = "dm-rejected-path-session";

        StompHeaderAccessor headers = StompHeaderAccessor.create(command);
        headers.setSessionId(sessionId);
        headers.setDestination(destination);

        if (command == StompCommand.SUBSCRIBE){
            headers.setSubscriptionId("dm-subscription");
        }

        Message<byte[]> message = MessageBuilder.createMessage(
                new byte[0],
                headers.getMessageHeaders()
        );

        MessageDeliveryException exception = new MessageDeliveryException(message, new AccessDeniedException("테스트용 DM 경로 접근 거부"));

        Message<byte[]> result = errorHandler.handleClientMessageProcessingError(message, exception);
        verify(errorSender).sendToSession(eq(sessionId), same(exception));
        verifyNoMoreInteractions(errorSender);
        assertThat(result).isNull();
    }

    @ParameterizedTest(name = "[{index}] command={0}, destination={1}")
    @CsvSource({
            "SEND, /pub/contents/00000000-0000-0000-0000-000000000001/chat",
            "SUBSCRIBE, /sub/contents/00000000-0000-0000-0000-000000000001/watch"
    })
    @DisplayName("콘텐츠 경로의 오류는 공통 오류 전송기를 호출하지 않고 기본 오류 응답을 반환한다.")
    void handleClientMessageProcessingError_usesDefaultHandlingForContentFrames(
            StompCommand command, String destination
    ){
        StompHeaderAccessor headers = StompHeaderAccessor.create(command);
        headers.setSessionId("content-test-session");
        headers.setDestination(destination);

        if (command == StompCommand.SUBSCRIBE){
            headers.setSubscriptionId("content-subscription");
        }

        Message<byte[]> message = MessageBuilder.createMessage(new byte[0], headers.getMessageHeaders());

        MessageDeliveryException exception = new MessageDeliveryException(message, new AccessDeniedException("테스트용 콘텐츠 접근 거부"));
        Message<byte[]> result = errorHandler.handleClientMessageProcessingError(message, exception);

        assertThat(result).isNotNull();
        StompHeaderAccessor resultHeaders = StompHeaderAccessor.wrap(result);
        assertThat(resultHeaders.getCommand()).isEqualTo(StompCommand.ERROR);
        assertThat(resultHeaders.getMessage()).isEqualTo(exception.getMessage());
        assertThat(result.getPayload()).isEmpty();
        verifyNoInteractions(errorSender);
    }

    @Test
    @DisplayName("CONNECT 인증 오류는 공통 오류 전송기를 호출하지 않고 기본 오류 응답을 반환한다.")
    void handleClientMessageProcessingError_usesDefaultHandlingForConnect(){
        StompHeaderAccessor headers = StompHeaderAccessor.create(StompCommand.CONNECT);
        headers.setSessionId("common-connect-session");
        Message<byte[]> message = MessageBuilder.createMessage(new byte[0], headers.getMessageHeaders());
        MessageDeliveryException exception = new MessageDeliveryException(message, new BadCredentialsException("테스트용 인증 실패"));

        Message<byte[]> result = errorHandler.handleClientMessageProcessingError(message, exception);
        assertThat(result).isNotNull();

        StompHeaderAccessor resultHeaders = StompHeaderAccessor.wrap(result);
        assertThat(resultHeaders.getCommand()).isEqualTo(StompCommand.ERROR);
        assertThat(resultHeaders.getMessage()).isEqualTo(exception.getMessage());
        assertThat(result.getPayload()).isEmpty();
        verifyNoInteractions(errorSender);
    }

    @Test
    @DisplayName("원본 메시지가 없으면 공통 오류 전송기를 호출하지 않고 기본 오류 응답을 반환한다.")
    void handleClientMessageProcessingError_usesDefaultHandlingWhenMessageIsMissing(){
        StompConversionException exception = new StompConversionException("테스트용 STOMP 프레임 해석 실패");
        Message<byte[]> result = errorHandler.handleClientMessageProcessingError(null, exception);

        assertThat(result).isNotNull();

        StompHeaderAccessor resultHeaders = StompHeaderAccessor.wrap(result);
        assertThat(resultHeaders.getCommand()).isEqualTo(StompCommand.ERROR);
        assertThat(resultHeaders.getMessage()).isEqualTo(exception.getMessage());
        assertThat(result.getPayload()).isEmpty();
        verifyNoInteractions(errorSender);
    }

    @Test
    @DisplayName("DM 메시지에 세션 ID가 없으면 공통 오류 전송기를 호출하지 않고 기본 오류 응답을 반환한다.")
    void  handleClientMessageProcessingError_usesDefaultHandlingWhenSessionIdIsMissing(){
        StompHeaderAccessor headers = StompHeaderAccessor.create(StompCommand.SEND);
        headers.setDestination("/pub/conversations/" + UUID.randomUUID() + "/direct-messages");
        Message<byte[]> message = MessageBuilder.createMessage(new byte[0], headers.getMessageHeaders());
        MessageDeliveryException exception = new MessageDeliveryException(message, new AccessDeniedException("테스트용 DM 접근 거부"));

        Message<byte[]> result = errorHandler.handleClientMessageProcessingError(message, exception);
        assertThat(result).isNotNull();

        StompHeaderAccessor resultHeaders = StompHeaderAccessor.wrap(result);
        assertThat(resultHeaders.getCommand()).isEqualTo(StompCommand.ERROR);
        assertThat(resultHeaders.getMessage()).isEqualTo(exception.getMessage());
        assertThat(result.getPayload()).isEmpty();
        verifyNoInteractions(errorSender);
    }

}
