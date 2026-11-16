package com.codeit.sb13.playlist_for_everyone.domain.conversation.service.impl;

import com.codeit.sb13.playlist_for_everyone.domain.conversation.mapper.DirectMessageErrorMapper;
import com.codeit.sb13.playlist_for_everyone.domain.conversation.service.DirectMessageErrorSender;
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

@Service
@Slf4j
public class DirectMessageErrorSenderImpl implements DirectMessageErrorSender {

    private final DirectMessageErrorMapper errorMapper;
    private final ObjectMapper objectMapper;
    private final MessageChannel clientOutboundChannel;
    private final WebSocketSessionRegistry webSocketSessionRegistry;

    public DirectMessageErrorSenderImpl(
            DirectMessageErrorMapper errorMapper,
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
                log.warn("DM 오류 프레임을 채널에 전달하지 못했습니다. sessionId={}", sessionId);
                closeSession(sessionId);
            }
        } catch (JacksonException | MessagingException e){
            log.warn("DM 오류 응답 직렬화 또는 채널 전송에 실패했습니다. sessionId={}, exceptionType={}",
                    sessionId, e.getClass().getSimpleName());
            closeSession(sessionId);
        }
    }

    private void closeSession(String sessionId){
        webSocketSessionRegistry.query(sessionId)
                .ifPresent(session -> disconnectSession(sessionId, session));
    }

    private void disconnectSession(String sessionId, WSSession session) {
        try {
            session.disconnect();
        } catch (IOException | RuntimeException e) {
            log.warn("DM 오류 전송 후 WebSocket세션 종료에 실패했습니다. sessionId={}, exceptionType={}",
                    sessionId, e.getClass().getSimpleName());
        }
    }
}
