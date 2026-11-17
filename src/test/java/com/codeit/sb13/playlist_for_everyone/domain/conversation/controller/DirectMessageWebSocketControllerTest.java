package com.codeit.sb13.playlist_for_everyone.domain.conversation.controller;

import com.codeit.sb13.playlist_for_everyone.domain.auth.security.SecurityUser;
import com.codeit.sb13.playlist_for_everyone.domain.conversation.dto.command.DirectMessageSendCommand;
import com.codeit.sb13.playlist_for_everyone.domain.conversation.dto.request.DirectMessageSendRequest;
import com.codeit.sb13.playlist_for_everyone.domain.conversation.service.DirectMessageErrorSender;
import com.codeit.sb13.playlist_for_everyone.domain.conversation.service.DirectMessageService;
import com.codeit.sb13.playlist_for_everyone.domain.user.entity.Role;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = DirectMessageWebSocketController.class)
@DisplayName("DM WebSocket 메시지 전송 컨트롤러")
class DirectMessageWebSocketControllerTest {

    @Autowired
    private DirectMessageWebSocketController controller;

    @MockitoBean
    private DirectMessageService directMessageService;

    @MockitoBean
    private DirectMessageErrorSender errorSender;

    @Test
    @DisplayName("대화방 ID와 인증된 사용자 ID, 본문을 서비스에 한 번 전달한다.")
    void send_passesAuthenticatedUserAndPayloadToService(){
        UUID conversationId = UUID.randomUUID();
        UUID requesterId = UUID.randomUUID();

        Authentication authentication = getAuthentication(requesterId);

        DirectMessageSendRequest request = new DirectMessageSendRequest("안녕하세요.");
        controller.send(conversationId, request, authentication);

        verify(directMessageService).send(new DirectMessageSendCommand(
                conversationId,
                requesterId,
                request.content()
        ));

        verifyNoMoreInteractions(directMessageService);

    }

    @Test
    @DisplayName("서비스에서 전송에 실패하면 원본예외를 그대로 전달하고 재시도하지 않는다.")
    void send_propagatesServiceExceptionWithoutRetrying(){
        UUID conversationId = UUID.randomUUID();
        UUID requesterId = UUID.randomUUID();

        Authentication authentication = getAuthentication(requesterId);
        DirectMessageSendRequest request = new DirectMessageSendRequest("안녕하세요.");

        DirectMessageSendCommand command = new DirectMessageSendCommand(conversationId, requesterId, request.content());

        DataIntegrityViolationException exception = new DataIntegrityViolationException("DM 저장 실패");
        given(directMessageService.send(command)).willThrow(exception);
        assertThatThrownBy(() -> controller.send(conversationId, request, authentication))
                .isSameAs(exception);

        verify(directMessageService).send(command);
        verifyNoMoreInteractions(directMessageService);

    }

    @Test
    @DisplayName("인증정보가 없으면 인증 예외를 발생시키고 서비스를 호출하지 않는다.")
    void send_rejectsMissingAuthenticationWithoutCallingService(){
        UUID conversationId = UUID.randomUUID();
        DirectMessageSendRequest request = new DirectMessageSendRequest("안녕하세요.");

        assertThatThrownBy(() -> controller.send(conversationId, request, null))
                .isInstanceOf(BadCredentialsException.class);

        verifyNoMoreInteractions(directMessageService);
    }

    @Test
    @DisplayName("principal이 SecurityUser가 아니면 인증 예외를 발생시키고 서비스를 호출하지 않는다.")
    void send_rejectsUnexpectedPrincipalWithoutCallingService(){
        UUID conversationId = UUID.randomUUID();
        DirectMessageSendRequest request = new DirectMessageSendRequest("안녕하세요.");

        SecurityUser user = getSecurityUser(UUID.randomUUID());
        Authentication authentication = new UsernamePasswordAuthenticationToken(
                user.getUsername(), null, user.getAuthorities());

        assertThatThrownBy(() -> controller.send(conversationId, request, authentication))
                .isInstanceOf(BadCredentialsException.class);

        verifyNoInteractions(directMessageService);
    }

    private SecurityUser getSecurityUser(UUID requesterId) {
        return new SecurityUser(
                requesterId,
                "sender@example.com",
                "encoded-password",
                Role.USER,
                false
        );
    }

    private Authentication getAuthentication(UUID requesterId) {
        SecurityUser user = getSecurityUser(requesterId);

        return new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities());
    }
}