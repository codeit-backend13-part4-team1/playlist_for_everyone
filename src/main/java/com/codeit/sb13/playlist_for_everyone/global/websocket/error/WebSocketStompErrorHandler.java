package com.codeit.sb13.playlist_for_everyone.global.websocket.error;

import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.messaging.Message;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.StompSubProtocolErrorHandler;

import java.util.List;

/**
 * 인바운드 채널 등의 처리 실패를 공통 오류 전송기로 연결하는 프로토콜 오류 처리기다.
 * 현재 목적지 목록에 포함된 DM SEND·SUBSCRIBE만 대상으로 하며, 그 밖에는 Spring 기본 처리로 위임한다.
 * 컨트롤러에서 처리된 예외는 이 경로 대신 해당 컨트롤러의 메시지 예외 처리 메서드를 거친다.
 */
@Component
@RequiredArgsConstructor
public class WebSocketStompErrorHandler extends StompSubProtocolErrorHandler {

    /*
     * 공통 STOMP 오류 응답을 적용할 목적지 접두사 목록.
     * 메시지 접근 권한을 결정하는 인증·인가 설정과는 별개다.
     *
     * 콘텐츠 담당자는 적용 범위를 확인한 뒤 필요한 접두사를 추가한다.
     * 예: /pub/contents/, /sub/contents/
     * 추가 시 오류 응답과 ERROR 전송 후 연결 종료 동작을 함께 검증한다.
     * 컨트롤러 내부 예외 처리는 별도의 메시지 예외 처리 연결이 필요하다.
     *
     * 연결 방법은 DirectMessageWebSocketController.handleException을 참고한다.
     * @MessageExceptionHandler에서 WebSocketErrorSender를 호출하여 요청 세션에 오류를 전송한다.
     * 별도 스레드의 비동기 이벤트 리스너에서 발생한 예외까지 자동으로 처리하지는 않는다.
     */
    private static final List<String> ERROR_HANDLING_DESTINATION_PREFIXES =
            List.of(
                    "/pub/conversations/",
                    "/sub/conversations/"
            );

    private final WebSocketErrorSender errorSender;

    @Override
    public @Nullable Message<byte[]> handleClientMessageProcessingError(@Nullable Message<byte[]> clientMessage, Throwable ex) {
        if (clientMessage == null){
            return super.handleClientMessageProcessingError(null, ex);
        }

        StompHeaderAccessor headers = StompHeaderAccessor.wrap(clientMessage);
        String sessionId = headers.getSessionId();
        if (sessionId == null || !isErrorHandlingTarget(headers)){
            return super.handleClientMessageProcessingError(clientMessage, ex);
        }

        errorSender.sendToSession(sessionId, ex);
        // 이미 전송기로 위임했으므로 기본 처리기가 또 다른 ERROR 프레임을 만들지 않도록 한다.
        return null;
    }

    private boolean isErrorHandlingTarget(StompHeaderAccessor headers){
        StompCommand command = headers.getCommand();
        if (command != StompCommand.SEND && command!= StompCommand.SUBSCRIBE){
            return false;
        }

        String destination = headers.getDestination();
        if (destination == null){
            return false;
        }

        return ERROR_HANDLING_DESTINATION_PREFIXES.stream()
                .anyMatch(destination::startsWith);
    }
}
