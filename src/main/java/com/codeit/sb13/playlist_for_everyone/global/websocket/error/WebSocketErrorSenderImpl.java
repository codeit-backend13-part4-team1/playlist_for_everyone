package com.codeit.sb13.playlist_for_everyone.global.websocket.error;

import com.codeit.sb13.playlist_for_everyone.global.dto.response.ApiErrorResponse;
import com.codeit.sb13.playlist_for_everyone.global.websocket.entity.WSSession;
import com.codeit.sb13.playlist_for_everyone.global.websocket.registry.session.WebSocketSessionRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessagingException;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Service;
import org.springframework.util.MimeTypeUtils;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;

/**
 * 공통 JSON 오류 본문을 세션 ID가 지정된 ERROR 프레임으로 만들어 아웃바운드 채널에 전달한다.
 *
 * <p>정상 채널 전달 이후의 ERROR 전송·연결 종료는 Spring의 STOMP 처리 흐름이 담당한다.
 * 직렬화·채널 전송 실패 또는 false 반환 시에는 이 구현체가 해당 연결을 직접 종료한다.
 * 채널 전달 성공을 클라이언트의 실제 수신 확인으로 해석하거나 여기서 재전송하지 않는다.
 */
@Service
@Slf4j
public class WebSocketErrorSenderImpl implements WebSocketErrorSender {

    private final WebSocketErrorMapper errorMapper;
    private final ObjectMapper objectMapper;
    private final MessageChannel clientOutboundChannel;
    private final WebSocketSessionRegistry webSocketSessionRegistry;

    public WebSocketErrorSenderImpl(
            WebSocketErrorMapper errorMapper,
            ObjectMapper objectMapper,
            @Qualifier("clientOutboundChannel")
            MessageChannel clientOutboundChannel,
            WebSocketSessionRegistry webSocketSessionRegistry) {
        this.errorMapper = errorMapper;
        this.objectMapper = objectMapper;
        this.clientOutboundChannel = clientOutboundChannel;
        this.webSocketSessionRegistry = webSocketSessionRegistry;
    }

    @Override
    public void sendToSession(String sessionId, Throwable exception) {

        ApiErrorResponse response = errorMapper.toErrorResponse(exception);

        try {
            byte[] payload = objectMapper.writeValueAsBytes(response);

            StompHeaderAccessor headers = StompHeaderAccessor.create(StompCommand.ERROR);
            headers.setSessionId(sessionId);
            headers.setContentType(MimeTypeUtils.APPLICATION_JSON);

            Message<byte[]> message = MessageBuilder.createMessage(payload, headers.getMessageHeaders());
            if (!clientOutboundChannel.send(message)){
                log.warn("WebSocket 오류 프레임을 채널에 전달하지 못했습니다. sessionId={}", sessionId);
                closeSession(sessionId);
            }
        } catch (JacksonException | MessagingException e){
            log.warn("WebSocket 오류 응답 직렬화 또는 채널 전송에 실패했습니다. sessionId={}, exceptionType={}",
                    sessionId, e.getClass().getSimpleName());
            closeSession(sessionId);
        }
    }

    /** 이미 정리된 세션이면 종료할 대상이 없다. 다른 세션을 찾아 대신 종료하지 않는다. */
    private void closeSession(String sessionId){
        webSocketSessionRegistry.query(sessionId)
                .ifPresent(session -> disconnectSession(sessionId, session));
    }

    /** 종료 중 예외는 기록한다. 레지스트리 해제는 실제 연결 종료를 관찰하는 공통 생명주기에서 수행한다. */
    private void disconnectSession(String sessionId, WSSession session) {
        try {
            session.disconnect();
        } catch (IOException | RuntimeException e) {
            log.warn("WebSocket 오류 전송 후 세션 종료에 실패했습니다. sessionId={}, exceptionType={}",
                    sessionId, e.getClass().getSimpleName());
        }
    }
}
