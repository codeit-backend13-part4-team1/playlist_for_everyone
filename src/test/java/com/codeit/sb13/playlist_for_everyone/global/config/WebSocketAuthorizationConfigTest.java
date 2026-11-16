package com.codeit.sb13.playlist_for_everyone.global.config;

import com.codeit.sb13.playlist_for_everyone.domain.auth.security.SecurityUser;
import com.codeit.sb13.playlist_for_everyone.domain.conversation.security.DirectMessageAuthorizationManager;
import com.codeit.sb13.playlist_for_everyone.domain.conversation.service.ConversationService;
import com.codeit.sb13.playlist_for_everyone.domain.user.entity.Role;
import com.codeit.sb13.playlist_for_everyone.global.exception.conversation.ConversationNotFoundException;
import org.jspecify.annotations.NonNull;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.messaging.Message;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessageType;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.authorization.AuthorizationManager;
import org.springframework.security.authorization.AuthorizationResult;
import org.springframework.security.core.Authentication;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.as;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.*;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = {
        WebSocketAuthorizationConfig.class,
        DirectMessageAuthorizationManager.class
})
@DisplayName("WebSocket 메시지 종류와 목적지별 인가 설정")
class WebSocketAuthorizationConfigTest {

    @Autowired
    @Qualifier("webSocketAuthorizationManager")
    private AuthorizationManager<Message<?>> authorizationManager;

    @MockitoBean
    private ConversationService conversationService;

    private final UUID conversationId = UUID.randomUUID();
    private final UUID requesterId = UUID.randomUUID();


    @Test
    @DisplayName("정상 DM SEND 경로는 대화방 참여 검증을 거쳐 접근을 허용한다.")
    void authorize_allowsParticipantSendToDirectMessageDestination(){

        SecurityUser user = getSecurityUser();

        Authentication authentication = new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities());
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SEND);
        accessor.setDestination("/pub/conversations/" + conversationId + "/direct-messages");
        accessor.setUser(authentication);
        Message<String> message = MessageBuilder.createMessage("", accessor.getMessageHeaders());
        AuthorizationResult result = authorizationManager.authorize(() -> authentication, message);
        assertThat(result).isNotNull();
        assertThat(result.isGranted()).isTrue();
        verify(conversationService).validateParticipant(conversationId, requesterId);
        verifyNoMoreInteractions(conversationService);
    }

    @Test
    @DisplayName("정상 DM SUBSCRIBE 경로는 대화방 참여 검증을 거쳐 구독을 허용한다.")
    void authorize_allowsParticipantSubscribeToDirectMessageDestination(){
        SecurityUser user = getSecurityUser();
        Authentication authentication = new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities());

        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
        accessor.setDestination("/sub/conversations/" + conversationId + "/direct-messages");
        accessor.setSubscriptionId("dm-subscription");
        accessor.setUser(authentication);

        Message<String> message = MessageBuilder.createMessage("", accessor.getMessageHeaders());
        AuthorizationResult result = authorizationManager.authorize(() -> authentication, message);

        assertThat(result).isNotNull();
        assertThat(result.isGranted()).isTrue();
        verify(conversationService).validateParticipant(conversationId, requesterId);
        verifyNoMoreInteractions(conversationService);
    }

    @ParameterizedTest(name = "[{index}] command={0}")
    @CsvSource({
            "SEND, /pub",
            "SUBSCRIBE, /sub"
    })
    @DisplayName("대화방 참여 검증에 실패하면 DM SEND와 SUBSCRIBE를 거부한다.")
    void authorize_deniesDirectMessageAccessWhenParticipantValidationFails(
            StompCommand command, String destinationPrefix
    ){
        SecurityUser user = getSecurityUser();
        Authentication authentication = new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities());
        StompHeaderAccessor accessor = StompHeaderAccessor.create(command);
        accessor.setDestination(destinationPrefix + "/conversations/" + conversationId + "/direct-messages");
        accessor.setUser(authentication);

        if (command== StompCommand.SUBSCRIBE){
            accessor.setSubscriptionId("dm-subscription");
        }
        Message<String> message = MessageBuilder.createMessage("", accessor.getMessageHeaders());

        ConversationNotFoundException exception = new ConversationNotFoundException(Map.of("conversationId", conversationId));

        willThrow(exception)
                .given(conversationService)
                .validateParticipant(conversationId, requesterId);

        AuthorizationResult result = authorizationManager.authorize(() -> authentication, message);
        assertThat(result).isNotNull();
        assertThat(result.isGranted()).isFalse();

        verify(conversationService).validateParticipant(conversationId, requesterId);
        verifyNoMoreInteractions(conversationService);
    }

    @ParameterizedTest(name = "[{index}] command={0}, prefix={1}")
    @CsvSource({
            "SEND, /sub",
            "SUBSCRIBE, /pub"
    })
    @DisplayName("DM 송신·구독 경로를 반대로 사용하면 참여 검증 없이 거부한다.")
    void authorize_deniesReversedDirectMessageDestinations(
            StompCommand command,
            String  destinationPrefix
    ){
        SecurityUser user = getSecurityUser();
        Authentication authentication = new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities());
        StompHeaderAccessor accessor = StompHeaderAccessor.create(command);
        accessor.setDestination(destinationPrefix + "/conversations/" + conversationId + "/direct-messages");
        accessor.setUser(authentication);

        if(command == StompCommand.SUBSCRIBE){
            accessor.setSubscriptionId("dm-subscription");
        }
        Message<String> message = MessageBuilder.createMessage("", accessor.getMessageHeaders());
        AuthorizationResult result = authorizationManager.authorize(() -> authentication, message);
        assertThat(result).isNotNull();
        assertThat(result.isGranted()).isFalse();

        verifyNoMoreInteractions(conversationService);
    }

    @ParameterizedTest(name = "[{index}] destination={0}")
    @ValueSource(strings = {
            "/sub/**",
            "/sub*/**",
            "/sub/{domain}/**",
            "/sub/conversations/**",
            "/sub/conversations/*/direct-messages",
            "/sub/conversations/{conversationId}/direct-messages"
    })
    @DisplayName("여러 대화방의 DM을 수신할 수 있는 패턴 구독은 참여 검증 없이 거부한다.")
    void authorize_deniesWildcardSubscriptionsThatCanReceiveDirectMessages(String destination){
        SecurityUser user = getSecurityUser();
        Authentication authentication = new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities());

        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
        accessor.setDestination(destination);
        accessor.setSubscriptionId("dm-subscription");
        accessor.setUser(authentication);

        Message<String> message = MessageBuilder.createMessage("", accessor.getMessageHeaders());
        AuthorizationResult result = authorizationManager.authorize(() -> authentication, message);

        assertThat(result).isNotNull();
        assertThat(result.isGranted()).isFalse();
        verifyNoInteractions(conversationService);
    }

    @ParameterizedTest(name = "[{index}] command={0}, destination={1}")
    @CsvSource({
            "SEND, /pub/conversations/%s/messages",
            "SEND, /pub/conversations/%s/direct-messages/extra",
            "SUBSCRIBE, /sub/conversations/%s/messages",
            "SUBSCRIBE, /sub/conversations/%s/direct-messages/extra"
    })
    @DisplayName("지원하지 않는 DM 경로는 참여 검증없이 거부한다.")
    void authorize_deniesUnsupportedDirectMessageDestinations(
            StompCommand command, String destinationTemplate){
        SecurityUser user = getSecurityUser();
        Authentication authentication = new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities());

        StompHeaderAccessor accessor = StompHeaderAccessor.create(command);
        accessor.setDestination(destinationTemplate.formatted(conversationId));
        accessor.setUser(authentication);

        if (command == StompCommand.SUBSCRIBE){
            accessor.setSubscriptionId("dm-subscription");
        }

        Message<String> message = MessageBuilder.createMessage("", accessor.getMessageHeaders());
        AuthorizationResult result = authorizationManager.authorize(() -> authentication, message);

        assertThat(result).isNotNull();
        assertThat(result.isGranted()).isFalse();
        verifyNoInteractions(conversationService);
    }

    @ParameterizedTest(name = "[{index}] command={0}, destination={1}")
    @CsvSource({
            "SEND, /pub/contents/%s/chat",
            "SUBSCRIBE, /sub/contents/%s/watch",
            "SUBSCRIBE, /sub/contents/%s/chat"
    })
    @DisplayName("콘텐츠 시청·채팅 경로는 DM 참여 검증 없이 통과시킨다.")
    void authorize_passesContentDestinationsWithoutDirectMessageValidation(
            StompCommand command, String destinationTemplate
    ){
        UUID contentId = UUID.randomUUID();
        SecurityUser user = getSecurityUser();
        Authentication authentication = new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities());

        StompHeaderAccessor accessor = StompHeaderAccessor.create(command);
        accessor.setDestination(destinationTemplate.formatted(contentId));
        accessor.setUser(authentication);

        if (command == StompCommand.SUBSCRIBE){
            accessor.setSubscriptionId("content-subscription");
        }

        Message<String> message = MessageBuilder.createMessage("", accessor.getMessageHeaders());

        AuthorizationResult result = authorizationManager.authorize(() -> authentication, message);
        assertThat(result).isNotNull();
        assertThat(result.isGranted()).isTrue();
        verifyNoInteractions(conversationService);
    }

    @ParameterizedTest(name = "[{index}] messageType={0}")
    @EnumSource(
            value = SimpMessageType.class,
            names = {"CONNECT", "DISCONNECT", "HEARTBEAT", "UNSUBSCRIBE"}
    )
    @DisplayName("연결·하트비트·구독 해제·연결 종료는 DM 참여 검증 없이 통과시킨다.")
    void authorize_passesConnectionManagementMessagesWithoutDirectMessageValidation(
            SimpMessageType messageType
    ){
        SimpMessageHeaderAccessor accessor = SimpMessageHeaderAccessor.create(messageType);
        accessor.setSessionId("dm-test-session");

        if (messageType == SimpMessageType.UNSUBSCRIBE){
            accessor.setSubscriptionId("dm-subscription");
        }

        Message<String> message = MessageBuilder.createMessage("", accessor.getMessageHeaders());
        AuthorizationResult result = authorizationManager.authorize(() -> null, message);

        assertThat(result).isNotNull();
        assertThat(result.isGranted()).isTrue();
        verifyNoInteractions(conversationService);
    }

    @ParameterizedTest(name = "[{index}] command={0}")
    @CsvSource({
            "SEND, /pub",
            "SUBSCRIBE, /sub"
    })
    @DisplayName("인증정보가 없으면 DM SEND와 SUBSCRIBE를 참여 검증 없이 거부한다.")
    void authorize_deniesDirectMessageAccessWhenAuthenticationIsMissing(
            StompCommand command, String destinationPrefix
    ){
        StompHeaderAccessor accessor = StompHeaderAccessor.create(command);
        accessor.setDestination(destinationPrefix+"/conversations/" + conversationId + "/direct-messages");

        if (command == StompCommand.SUBSCRIBE){
            accessor.setSubscriptionId("dm-subscription");
        }
        Message<String> message = MessageBuilder.createMessage("", accessor.getMessageHeaders());
        AuthorizationResult result = authorizationManager.authorize(() -> null, message);

        assertThat(result).isNotNull();
        assertThat(result.isGranted()).isFalse();
        verifyNoInteractions(conversationService);
    }



    private SecurityUser getSecurityUser() {
        return new SecurityUser(
                requesterId,
                "sender@example.com",
                "encoded-password",
                Role.USER,
                false
        );
    }
}
