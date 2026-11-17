package com.codeit.sb13.playlist_for_everyone.domain.conversation.controller;

import com.codeit.sb13.playlist_for_everyone.domain.auth.security.SecurityUser;
import com.codeit.sb13.playlist_for_everyone.domain.conversation.dto.request.DirectMessageSendRequest;
import com.codeit.sb13.playlist_for_everyone.global.websocket.error.WebSocketErrorSender;
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

/**
 * DM SEND 본문을 검증하고 STOMP 인증 객체의 사용자 ID로 송신 명령을 구성한다.
 *
 * <p>발신자·수신자 ID는 클라이언트 본문에서 받지 않는다. /pub 접두사는 WebSocketConfig가 처리한다.
 * 정상 메시지 전달은 서비스의 Publisher를 거치므로 이 컨트롤러에서 별도로 브로드캐스트하지 않는다.
 */
@Controller
@RequiredArgsConstructor
public class DirectMessageWebSocketController {

    private final DirectMessageService directMessageService;
    private final WebSocketErrorSender errorSender;


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

    /**
     * 메시지 매핑 중 입력 변환·검증 또는 서비스 실행에서 발생한 예외를 요청 세션에 전달한다.
     * 공통 프로토콜 오류 처리기의 목적지 목록만 확장해도 이 처리가 자동 연결되는 것은 아니다.
     * 다른 도메인의 메시지 컨트롤러도 공통 WebSocketErrorSender를 호출하는 연결을 별도로 구성한다.
     */
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
