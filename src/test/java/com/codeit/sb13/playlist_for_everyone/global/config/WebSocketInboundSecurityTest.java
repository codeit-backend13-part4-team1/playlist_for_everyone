package com.codeit.sb13.playlist_for_everyone.global.config;

import com.codeit.sb13.playlist_for_everyone.domain.auth.security.SecurityUser;
import com.codeit.sb13.playlist_for_everyone.domain.conversation.security.DirectMessageAuthorizationManager;
import com.codeit.sb13.playlist_for_everyone.domain.conversation.service.ConversationService;
import com.codeit.sb13.playlist_for_everyone.domain.user.entity.Role;
import com.codeit.sb13.playlist_for_everyone.global.exception.conversation.ConversationNotFoundException;
import com.codeit.sb13.playlist_for_everyone.global.websocket.WebSocketInterceptor;
import com.codeit.sb13.playlist_for_everyone.global.websocket.registry.session.WebSocketSessionRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageDeliveryException;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessageType;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.web.socket.messaging.StompSubProtocolErrorHandler;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.*;

@ExtendWith(SpringExtension.class)
@WebAppConfiguration
@ContextConfiguration(classes = {
        WebSocketConfig.class,
        WebSocketAuthorizationConfig.class,
        DirectMessageAuthorizationManager.class
})
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@DisplayName("WebSocket 인바운드 채널 인가")
public class WebSocketInboundSecurityTest {

    @Autowired
    @Qualifier("clientInboundChannel")
    private MessageChannel clientInboundChannel;

    @MockitoBean
    private ConversationService conversationService;

    @MockitoBean
    private WebSocketInterceptor webSocketInterceptor;

    @MockitoBean
    private WebSocketSessionRegistry webSocketSessionRegistry;

    @MockitoBean(name = "webSocketStompErrorHandler")
    private StompSubProtocolErrorHandler stompErrorHandler;

    private final UUID conversationId = UUID.randomUUID();
    private final UUID requesterId = UUID.randomUUID();

    @BeforeEach
    void setUp(){
        doAnswer(invocation -> invocation.getArgument(0))
                .when(webSocketInterceptor)
                .preSend(any(Message.class), same(clientInboundChannel));
    }

    @AfterEach
    void tearDown(){
        SecurityContextHolder.clearContext();
    }

    @ParameterizedTest(name = "[{index}] command={0}")
    @CsvSource({
            "SEND, /pub",
            "SUBSCRIBE, /sub"
    })
    @DisplayName("대화방 비참여자의 DM 전송과 구독은 인바운드 채널에서 거부한다.")
    void send_rejectsNonParticipantDirectMessageFrames(StompCommand command, String destinationPrefix){
        Authentication authentication = getAuthentication();

        StompHeaderAccessor accessor = StompHeaderAccessor.create(command);
        accessor.setDestination(destinationPrefix + "/conversations/" + conversationId + "/direct-messages");
        accessor.setSessionId("dm-test-session");
        accessor.setSessionAttributes(new HashMap<>());
        accessor.setUser(authentication);
        if (command == StompCommand.SUBSCRIBE){
            accessor.setSubscriptionId("dm-subscription");
        }

        accessor.setLeaveMutable(true);

        Message<byte[]> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

        willThrow(new ConversationNotFoundException(Map.of("conversationId", conversationId)))
                .given(conversationService)
                .validateParticipant(conversationId, requesterId);

        assertThatThrownBy(() -> clientInboundChannel.send(message))
                .isInstanceOf(MessageDeliveryException.class)
                .hasCauseInstanceOf(AccessDeniedException.class);

        verify(conversationService)
                .validateParticipant(conversationId, requesterId);
        verifyNoMoreInteractions(conversationService);

    }

    @ParameterizedTest(name = "[{index}] command={0}")
    @CsvSource({
            "SEND, /pub",
            "SUBSCRIBE, /sub"
    })
    @DisplayName("대화방 참여자의 DM 전송과 구독은 참여 검증 후 인바운드 채널을 통과한다.")
    void send_allowsParticipantDirectMessageFrames(
            StompCommand command, String destinationPrefix
    ){
        Authentication authentication = getAuthentication();

        StompHeaderAccessor accessor = StompHeaderAccessor.create(command);
        accessor.setDestination(destinationPrefix + "/conversations/" + conversationId + "/direct-messages");
        accessor.setSessionId("dm-test-session");
        accessor.setSessionAttributes(new HashMap<>());
        accessor.setUser(authentication);
        if (command == StompCommand.SUBSCRIBE){
            accessor.setSubscriptionId("dm-subscription");
        }

        accessor.setLeaveMutable(true);

        Message<byte[]> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
        assertThat(clientInboundChannel.send(message)).isTrue();
        verify(conversationService).validateParticipant(conversationId, requesterId);
        verifyNoMoreInteractions(conversationService);
    }

    @ParameterizedTest(name = "[{index}] command={0}")
    @CsvSource({
            "SEND, /pub",
            "SUBSCRIBE, /sub"
    })
    @DisplayName("인증 정보가 없는 DM 전송과 구독은 참여 검증 없이 거부한다.")
    void send_rejectsDirectMessageFramesWithoutAuthentication(
            StompCommand command,
            String destinationPrefix
    ){
        StompHeaderAccessor accessor = StompHeaderAccessor.create(command);
        accessor.setDestination(destinationPrefix + "/conversations/" + conversationId + "/direct-messages");
        accessor.setSessionId("dm-test-session");
        accessor.setSessionAttributes(new HashMap<>());

        if (command == StompCommand.SUBSCRIBE){
            accessor.setSubscriptionId("dm-subscription");
        }
        accessor.setLeaveMutable(true);
        Message<byte[]> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

        assertThatThrownBy(() -> clientInboundChannel.send(message))
                .isInstanceOf(MessageDeliveryException.class)
                .hasCauseInstanceOf(AccessDeniedException.class);

        verifyNoInteractions(conversationService);
    }

    @Test
    @DisplayName("인증된 사용자도 여러 대화방을 수신할 수 있는 패턴 구독은 참여 검증 없이 거부한다.")
    void send_rejectsWildcardSubscriptionBeforeParticipantValidation(){
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
        accessor.setDestination("/sub/**");
        accessor.setSessionId("dm-test-session");
        accessor.setSessionAttributes(new HashMap<>());
        accessor.setSubscriptionId("dm-subscription");
        accessor.setUser(getAuthentication());
        accessor.setLeaveMutable(true);
        Message<byte[]> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

        assertThatThrownBy(() -> clientInboundChannel.send(message))
                .isInstanceOf(MessageDeliveryException.class)
                .hasCauseInstanceOf(AccessDeniedException.class);

        verifyNoInteractions(conversationService);
    }

    @ParameterizedTest(name = "[{index}] command={0}, destination={1}")
    @CsvSource({
            "SEND, /pub/contents/%s/chat",
            "SUBSCRIBE, /sub/contents/%s/watch",
            "SUBSCRIBE, /sub/contents/%s/chat"
    })
    @DisplayName("콘텐츠 채팅과 시청 경로는 DM 참여 검증 없이 인바운드 채널을 통과한다.")
    void send_allowsContentFramesWithoutDirectMessageValidation(
            StompCommand command,
            String destinationTemplate
    ){
        UUID contentId = UUID.randomUUID();

        StompHeaderAccessor accessor = StompHeaderAccessor.create(command);

        accessor.setDestination(destinationTemplate.formatted(contentId));
        accessor.setSessionId("content-test-session");
        accessor.setSessionAttributes(new HashMap<>());
        accessor.setUser(getAuthentication());

        if (command == StompCommand.SUBSCRIBE){
            accessor.setSubscriptionId("content-subscription");
        }

        accessor.setLeaveMutable(true);
        Message<byte[]> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
        assertThat(clientInboundChannel.send(message)).isTrue();
        verifyNoInteractions(conversationService);

    }

    @ParameterizedTest(name = "[{index}] messageType={0}")
    @EnumSource(value = SimpMessageType.class,
    names = {
            "CONNECT",
            "HEARTBEAT",
            "UNSUBSCRIBE",
            "DISCONNECT"
    })
    @DisplayName("연결·하트비트·구독 해제·연결 종료는 DM 참여 검증 없이 인바운드 채널을 통과한다.")
    void send_allowsConnectionManagementFramesWithoutDirectMessageValidation(SimpMessageType messageType){
        SimpMessageHeaderAccessor accessor = SimpMessageHeaderAccessor.create(messageType);
        accessor.setSessionId("dm-control-test-session");
        accessor.setSessionAttributes(new HashMap<>());

        if (messageType == SimpMessageType.UNSUBSCRIBE){
            accessor.setSubscriptionId("dm-subscription");
        }

        accessor.setLeaveMutable(true);
        Message<byte[]> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
        assertThat(clientInboundChannel.send(message)).isTrue();
        verifyNoInteractions(conversationService);
    }

    private Authentication getAuthentication() {
        SecurityUser user = new SecurityUser(
                requesterId,
                "sender@example.com",
                "encoded-password",
                Role.USER,
                false
        );

        return new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities());
    }

}
