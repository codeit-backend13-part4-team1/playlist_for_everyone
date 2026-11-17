package com.codeit.sb13.playlist_for_everyone.domain.conversation.controller;

import com.codeit.sb13.playlist_for_everyone.domain.auth.security.SecurityUser;
import com.codeit.sb13.playlist_for_everyone.domain.conversation.dto.request.DirectMessageSendRequest;
import com.codeit.sb13.playlist_for_everyone.domain.conversation.service.DirectMessageErrorSender;
import com.codeit.sb13.playlist_for_everyone.domain.conversation.service.DirectMessageService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.handler.annotation.*;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;

import java.util.Optional;
import java.util.UUID;

@Controller
@RequiredArgsConstructor
public class DirectMessageWebSocketController {

    private final DirectMessageService directMessageService;
    private final DirectMessageErrorSender errorSender;


    @MessageMapping("/conversations/{conversationId}/direct-messages")
    public void send(
            @DestinationVariable("conversationId")
            UUID conversationId,
            @Payload @Valid
            DirectMessageSendRequest request,
            Authentication authentication){

        SecurityUser user = resolveSecurityUser(authentication)
                .orElseThrow(() -> new BadCredentialsException("인증된 사용자 정보를 확인할 수 없습니다."));

        directMessageService.send(request.toCommand(conversationId, user.getUserId()));
    }

    @MessageExceptionHandler(Exception.class)
    public void handleException(
            Exception exception,
            @Header(SimpMessageHeaderAccessor.SESSION_ID_HEADER)
            String sessionId){
        errorSender.sendToSession(sessionId, exception);
    }

    private Optional<SecurityUser> resolveSecurityUser(Authentication authentication) {
        if (authentication != null
                && authentication.getPrincipal() instanceof SecurityUser user){
            return Optional.of(user);
        }
        return Optional.empty();
    }
}
