package com.codeit.sb13.playlist_for_everyone.domain.conversation.security;

import com.codeit.sb13.playlist_for_everyone.domain.auth.security.SecurityUser;
import com.codeit.sb13.playlist_for_everyone.domain.conversation.service.ConversationService;
import com.codeit.sb13.playlist_for_everyone.domain.user.entity.Role;
import com.codeit.sb13.playlist_for_everyone.global.exception.conversation.ConversationNotFoundException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.authorization.AuthorizationManager;
import org.springframework.security.authorization.AuthorizationResult;
import org.springframework.security.core.Authentication;
import org.springframework.security.messaging.access.intercept.MessageAuthorizationContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.*;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = DirectMessageAuthorizationManager.class)
@DisplayName("DM WebSocket 접근 권한 판단")
class DirectMessageAuthorizationManagerTest {

    @Autowired
    private AuthorizationManager<MessageAuthorizationContext<?>> authorizationManager;

    @MockitoBean
    private ConversationService conversationService;

    private final UUID conversationId = UUID.randomUUID();
    private final UUID requesterId = UUID.randomUUID();

    @ParameterizedTest
    @EnumSource(value = Role.class, names = {"USER", "ADMIN"})
    @DisplayName("인증된 대화방 참여자는 사용자 권한과 관계없이 접근을 허용한다.")
    void authorize_grantsAccessToAuthenticatedParticipant(Role role){
        Authentication authentication = getAuthentication(role);
        MessageAuthorizationContext<String> context = createMessageAuthorizationContext();
        AuthorizationResult result = authorizationManager.authorize(() -> authentication, context);
        assertThat(result).isNotNull();
        assertThat(result.isGranted()).isTrue();

        verify(conversationService).validateParticipant(conversationId, requesterId);
        verifyNoMoreInteractions(conversationService);
    }

    @ParameterizedTest
    @EnumSource(value = Role.class, names = {"USER", "ADMIN"})
    @DisplayName("대화방이 없거나 참여자가 아니면 사용자 권한과 관계없이 접근을 거부한다.")
    void authorize_deniesAccessWhenParticipantValidationFails(Role role){
        Authentication authentication = getAuthentication(role);
        MessageAuthorizationContext<String> context = createMessageAuthorizationContext();

        ConversationNotFoundException exception = new ConversationNotFoundException(Map.of("conversationId", conversationId));

        willThrow(exception)
                .given(conversationService)
                .validateParticipant(conversationId, requesterId);

        AuthorizationResult result = authorizationManager.authorize(() -> authentication, context);

        assertThat(result).isNotNull();
        assertThat(result.isGranted()).isFalse();

        verify(conversationService).validateParticipant(conversationId, requesterId);
        verifyNoMoreInteractions(conversationService);
    }

    @Test
    @DisplayName("인증정보가 없으면 접근을 거부하고 대화방 참여 검증을 호출하지 않는다.")
    void authorize_deniesAccessWhenAuthenticationIsMissing(){

        MessageAuthorizationContext<String> context = createMessageAuthorizationContext();

        AuthorizationResult result = authorizationManager.authorize(() -> null, context);

        assertThat(result).isNotNull();
        assertThat(result.isGranted()).isFalse();
        verifyNoInteractions(conversationService);
    }

    @Test
    @DisplayName("인증이 완료되지 않으면 접근을 거부하고 대화방 참여 검증을 호출하지 않는다.")
    void authorize_deniesAccessWhenAuthenticationIsNotAuthenticated(){
        SecurityUser user = getSecurityUser(requesterId, Role.USER);
        Authentication authentication = new UsernamePasswordAuthenticationToken(user, null);

        MessageAuthorizationContext<String> context = createMessageAuthorizationContext();
        AuthorizationResult result = authorizationManager.authorize(() -> authentication, context);

        assertThat(result).isNotNull();
        assertThat(result.isGranted()).isFalse();
        verifyNoInteractions(conversationService);
    }

    @Test
    @DisplayName("principal이 SecurityUser가 아니면 접근을 거부하고 대화방 참여 검증을 호출하지 않는다.")
    void authorize_deniesAccessWhenPrincipalIsUnexpectedType(){
        SecurityUser user= getSecurityUser(requesterId, Role.USER);
        Authentication authentication = new UsernamePasswordAuthenticationToken(user.getUsername(), null, user.getAuthorities());

        MessageAuthorizationContext<String> context = createMessageAuthorizationContext();

        AuthorizationResult result = authorizationManager.authorize(() -> authentication, context);

        assertThat(result).isNotNull();
        assertThat(result.isGranted()).isFalse();
        verifyNoInteractions(conversationService);
    }

    @Test
    @DisplayName("잠긴 계정이면 접근을 거부하고 대화방 참여 검증을 호출하지 않는다.")
    void authorize_deniesAccessWhenAccountIsLocked(){
        Authentication authentication = getAuthentication(Role.USER, true);
        MessageAuthorizationContext<String> context = createMessageAuthorizationContext();
        AuthorizationResult result = authorizationManager.authorize(() -> authentication, context);

        assertThat(result).isNotNull();
        assertThat(result.isGranted()).isFalse();
        verifyNoInteractions(conversationService);
    }

    @Test
    @DisplayName("대화방 ID가 누락되면 접근을 거부하고 대화방 참여 검증을 호출하지 않는다.")
    void authorize_deniesAccessWhenConversationIdIsMissing(){
        Authentication authentication = getAuthentication();

        MessageAuthorizationContext<String> context = new MessageAuthorizationContext<>(
                MessageBuilder.withPayload("").build(),
                Map.of()
        );

        AuthorizationResult result = authorizationManager.authorize(() -> authentication, context);

        assertThat(result).isNotNull();
        assertThat(result.isGranted()).isFalse();

        verifyNoInteractions(conversationService);
    }

    @ParameterizedTest(name ="[{index}] conversationId={0}")
    @ValueSource(strings = {
            "not-a-uuid",
            "1-1-1-1-1",
            "ABCDEFAB-1234-5678-9ABC-DEF012345678",
            "abcdefab-1234-5678-9Abc-def012345678",
            "*",
            "**"
    })
    @DisplayName("대화방 ID 형식이 잘못되면 접근을 거부하고 대화방 참여 검증을 호출하지 않는다.")
    void authorize_deniesAccessWhenConversationIdIsInvalid(String invalidConversationId){
        Authentication authentication = getAuthentication();
        MessageAuthorizationContext<String> context = createMessageAuthorizationContext(invalidConversationId);

        AuthorizationResult result = authorizationManager.authorize(() -> authentication, context);
        assertThat(result).isNotNull();
        assertThat(result.isGranted()).isFalse();

        verifyNoInteractions(conversationService);
    }

    @Test
    @DisplayName("대화방 참여 검증 중 DB 조회에 실패하면 원본 예외를 전달하고 재시도하지 않는다.")
    void authorize_propagatesParticipantLookupFailure(){
        Authentication authentication = getAuthentication();
        MessageAuthorizationContext<String> context = createMessageAuthorizationContext();

        DataAccessResourceFailureException exception = new DataAccessResourceFailureException("대화방 참여 검증 중 DB 연결 실패");

        willThrow(exception)
                .given(conversationService)
                .validateParticipant(conversationId, requesterId);

        assertThatThrownBy(() -> authorizationManager.authorize(() -> authentication, context))
                .isSameAs(exception);

        verify(conversationService).validateParticipant(conversationId, requesterId);
        verifyNoMoreInteractions(conversationService);
    }


    private Authentication getAuthentication() {
        return getAuthentication(Role.USER, false);
    }

    private Authentication getAuthentication(Role role) {
        return getAuthentication(role, false);
    }

    private Authentication getAuthentication(Role role, boolean isLocked) {
        SecurityUser user = getSecurityUser(requesterId, role, isLocked);
        return new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities());
    }

    private MessageAuthorizationContext<String> createMessageAuthorizationContext(String conversationId) {
        return new MessageAuthorizationContext<>(
                MessageBuilder.withPayload("").build(),
                Map.of("conversationId", conversationId)
        );
    }

    private MessageAuthorizationContext<String> createMessageAuthorizationContext() {
        return createMessageAuthorizationContext(conversationId.toString());
    }

    private SecurityUser getSecurityUser(UUID requesterId, Role role) {
        return getSecurityUser(requesterId, role, false);
    }

    private SecurityUser getSecurityUser(UUID requesterId, Role role, boolean isLocked) {
        return new SecurityUser(
                requesterId,
                "sender@example.com",
                "encoded-password",
                role,
                isLocked
        );
    }
}
