package com.codeit.sb13.playlist_for_everyone.domain.conversation.controller;

import com.codeit.sb13.playlist_for_everyone.domain.auth.security.SecurityUser;
import com.codeit.sb13.playlist_for_everyone.domain.conversation.dto.command.DirectMessageSendCommand;
import com.codeit.sb13.playlist_for_everyone.domain.conversation.service.DirectMessageErrorSender;
import com.codeit.sb13.playlist_for_everyone.domain.conversation.service.DirectMessageService;
import com.codeit.sb13.playlist_for_everyone.domain.user.entity.Role;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.messaging.Message;
import org.springframework.messaging.converter.JacksonJsonMessageConverter;
import org.springframework.messaging.converter.MessageConversionException;
import org.springframework.messaging.handler.HandlerMethod;
import org.springframework.messaging.handler.annotation.support.MethodArgumentNotValidException;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.annotation.support.SimpAnnotationMethodMessageHandler;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ExecutorSubscribableChannel;
import org.springframework.messaging.support.MessageBuilder;
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
import static org.mockito.Mockito.*;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = {
        DirectMessageWebSocketController.class,
        LocalValidatorFactoryBean.class
})
@DisplayName("DM WebSocket 메시지 매핑 및 입력 검증")
public class DirectMessageWebSocketMessageMappingTest {

    @Autowired
    private ApplicationContext applicationContext;

    @Autowired
    private LocalValidatorFactoryBean validator;

    @MockitoBean
    private DirectMessageService directMessageService;

    @MockitoBean
    private DirectMessageErrorSender errorSender;

    private RecordingMessageHandler messageHandler;

    private static class RecordingMessageHandler extends SimpAnnotationMethodMessageHandler{

        private Exception lastException;

        RecordingMessageHandler(){
            super(new ExecutorSubscribableChannel(),
                    new ExecutorSubscribableChannel(),
                    new SimpMessagingTemplate(new ExecutorSubscribableChannel()));
        }

        @Override
        protected void processHandlerMethodException(HandlerMethod handlerMethod, Exception exception, Message<?> message) {
            this.lastException = exception;
        }

        Exception getLastException(){
            return lastException;
        }
    }

    @BeforeEach
    void setUp(){
        messageHandler = new RecordingMessageHandler();
        messageHandler.setApplicationContext(applicationContext);
        messageHandler.setDestinationPrefixes(List.of("/pub"));
        messageHandler.setMessageConverter(new JacksonJsonMessageConverter());
        messageHandler.setValidator(validator);
        messageHandler.afterPropertiesSet();
    }

    @Test
    @DisplayName("DM 전송 경로의 JSON 본문과 인증 사용자 정보를 서비스에 전달한다.")
    void send_routesJsonPayloadToService(){
        UUID conversationId = UUID.randomUUID();
        UUID requesterId = UUID.randomUUID();

        Message<byte[]> message = createSendMessage(conversationId, requesterId,
                """
                        {"content": "안녕하세요."}
                        """);

        messageHandler.handleMessage(message);

        assertThat(messageHandler.getLastException()).isNull();
        verify(directMessageService).send(new DirectMessageSendCommand(conversationId, requesterId, "안녕하세요."));
        verifyNoMoreInteractions(directMessageService);
        verifyNoInteractions(errorSender);
    }

    @Test
    @DisplayName("공백 본문은 입력 검증에서 거절하고 서비스를 호출하지 않는다.")
    void send_rejectsBlankContentBeforeCallingService(){
        Message<byte[]> message = createSendMessage(UUID.randomUUID(), UUID.randomUUID(),
                """
                        {"content": "   "}
                        """);

        messageHandler.handleMessage(message);

        assertThat(messageHandler.getLastException())
                .isInstanceOfSatisfying(MethodArgumentNotValidException.class,
                        e -> {
                    assertThat(e.getBindingResult()).isNotNull();
                    assertThat(e.getBindingResult().getFieldError("content"))
                            .isNotNull();

                });
        verifyNoInteractions(directMessageService);
    }

    @Test
    @DisplayName("잘못된 JSON은 메시지 변환에서 거절하고 서비스를 호출하지 않는다.")
    void send_rejectsMalformedJsonBeforeCallingService(){
        Message<byte[]> message = createSendMessage(
                UUID.randomUUID(),
                UUID.randomUUID(),
                "{invalid-json}"
        );

        messageHandler.handleMessage(message);
        assertThat(messageHandler.getLastException())
                .isInstanceOf(MessageConversionException.class);
        verifyNoInteractions(directMessageService);
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
