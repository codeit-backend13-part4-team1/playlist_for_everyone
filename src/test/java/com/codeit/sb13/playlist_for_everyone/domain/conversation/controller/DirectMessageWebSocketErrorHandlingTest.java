package com.codeit.sb13.playlist_for_everyone.domain.conversation.controller;

import com.codeit.sb13.playlist_for_everyone.domain.auth.security.SecurityUser;
import com.codeit.sb13.playlist_for_everyone.domain.conversation.dto.command.DirectMessageSendCommand;
import com.codeit.sb13.playlist_for_everyone.global.websocket.error.WebSocketErrorSender;
import com.codeit.sb13.playlist_for_everyone.domain.conversation.service.DirectMessageService;
import com.codeit.sb13.playlist_for_everyone.domain.user.entity.Role;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.messaging.Message;
import org.springframework.messaging.converter.JacksonJsonMessageConverter;
import org.springframework.messaging.converter.MessageConversionException;
import org.springframework.messaging.handler.annotation.support.MethodArgumentNotValidException;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.annotation.support.SimpAnnotationMethodMessageHandler;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ExecutorSubscribableChannel;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.util.MimeTypeUtils;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.*;


@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = {
        DirectMessageWebSocketController.class,
        LocalValidatorFactoryBean.class
})
public class DirectMessageWebSocketErrorHandlingTest {

    @Autowired
    private ApplicationContext applicationContext;

    @Autowired
    private LocalValidatorFactoryBean validator;

    @MockitoBean
    private DirectMessageService directMessageService;

    @MockitoBean
    private WebSocketErrorSender errorSender;

    private SimpAnnotationMethodMessageHandler messageHandler;

    @BeforeEach
    void setUp(){
        messageHandler = new SimpAnnotationMethodMessageHandler(
                new ExecutorSubscribableChannel(),
                new ExecutorSubscribableChannel(),
                new SimpMessagingTemplate(new ExecutorSubscribableChannel())
        );

        messageHandler.setApplicationContext(applicationContext);
        messageHandler.setDestinationPrefixes(List.of("/pub"));
        messageHandler.setMessageConverter(new JacksonJsonMessageConverter());
        messageHandler.setValidator(validator);
        messageHandler.afterPropertiesSet();
    }

    @Test
    @DisplayName("DM 서비스 예외를 요청한 세션의 오류 전송기로 한 번 전달한다.")
    void send_forwardsServiceExceptionToRequestingSession(){
        UUID conversationId = UUID.randomUUID();
        UUID requesterId = UUID.randomUUID();

        DirectMessageSendCommand command = new DirectMessageSendCommand(conversationId, requesterId, "안녕하세요.");

        DataIntegrityViolationException exception = new DataIntegrityViolationException("테스트용 DM 저장 실패");
        when(directMessageService.send(command)).thenThrow(exception);

        Message<byte[]> message = createSendMessage(
                conversationId,
                requesterId,
                """
                        {"content": "안녕하세요."}
                        """
        );

        messageHandler.handleMessage(message);
        verify(directMessageService).send(command);
        verify(errorSender).sendToSession(
                eq("dm-test-session"),
                same(exception)
        );
        verifyNoMoreInteractions(directMessageService, errorSender);
    }

    @Test
    @DisplayName("공백 본문의 입력 검증 예외를 요청한 세션의 오류 전송기로 전달한다.")
    void send_forwardsValidationExceptionToRequestingSession(){
        Message<byte[]> message = createSendMessage(
                UUID.randomUUID(),
                UUID.randomUUID(),
                """
                        {"content": "   "}
                        """
        );

        messageHandler.handleMessage(message);
        ArgumentCaptor<Throwable> exceptionCaptor = ArgumentCaptor.forClass(Throwable.class);

        verify(errorSender).sendToSession(
                eq("dm-test-session"),
                exceptionCaptor.capture()
        );
        assertThat(exceptionCaptor.getValue()).isInstanceOf(MethodArgumentNotValidException.class);
        verifyNoInteractions(directMessageService);
        verifyNoMoreInteractions(errorSender);

    }

    @Test
    @DisplayName("잘못된 JSON의 변환 예외를 요청한 세션의 오류 전송기로 전달한다.")
    void send_forwardsMessageConversionExceptionToRequestingSession(){
        Message<byte[]> message = createSendMessage(
                UUID.randomUUID(),
                UUID.randomUUID(),
                "{invalid-json}"
        );

        messageHandler.handleMessage(message);
        ArgumentCaptor<Throwable> exceptionCaptor = ArgumentCaptor.forClass(Throwable.class);
        verify(errorSender).sendToSession(eq("dm-test-session"), exceptionCaptor.capture());

        assertThat(exceptionCaptor.getValue()).isInstanceOf(MessageConversionException.class);
        verifyNoInteractions(directMessageService);
        verifyNoMoreInteractions(errorSender);
    }

    @Test
    @DisplayName("인증정보 누락 예외를 요청한 세션의 오류 전송기로 전달한다.")
    void send_forwardsAuthenticationExceptionToRequestingSession(){
        Message<byte[]> authenticatedMessage = createSendMessage(
                UUID.randomUUID(),
                UUID.randomUUID(),
                """
                        {"content": "안녕하세요."}
                        """
        );

        StompHeaderAccessor headers = StompHeaderAccessor.wrap(authenticatedMessage);
        headers.setUser(null);
        headers.setLeaveMutable(true);

        Message<byte[]> message = MessageBuilder.createMessage(authenticatedMessage.getPayload(),
                headers.getMessageHeaders());

        messageHandler.handleMessage(message);

        ArgumentCaptor<Throwable> exceptionCaptor = ArgumentCaptor.forClass(Throwable.class);

        verify(errorSender).sendToSession(eq("dm-test-session"), exceptionCaptor.capture());
        assertThat(exceptionCaptor.getValue()).isInstanceOf(BadCredentialsException.class);

        verifyNoInteractions(directMessageService);
        verifyNoMoreInteractions(errorSender);
    }

    private Message<byte[]> createSendMessage(
            UUID conversationId,
            UUID requesterId,
            String json
    ){
        SecurityUser user = new SecurityUser(requesterId,
                "sender@example.com",
                "encoded-password",
                Role.USER,
                false);

        Authentication authentication = new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities());
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SEND);

        accessor.setDestination("/pub/conversations/" + conversationId + "/direct-messages");
        accessor.setSessionId("dm-test-session");
        accessor.setSessionAttributes(new HashMap<>());
        accessor.setUser(authentication);
        accessor.setContentType(MimeTypeUtils.APPLICATION_JSON);
        accessor.setLeaveMutable(true);

        return MessageBuilder.createMessage(
                json.getBytes(StandardCharsets.UTF_8),
                accessor.getMessageHeaders()
        );
    }
}
