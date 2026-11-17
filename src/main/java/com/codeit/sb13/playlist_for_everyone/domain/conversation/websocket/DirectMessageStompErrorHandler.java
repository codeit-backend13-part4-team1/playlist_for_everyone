package com.codeit.sb13.playlist_for_everyone.domain.conversation.websocket;

import com.codeit.sb13.playlist_for_everyone.domain.conversation.service.DirectMessageErrorSender;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.messaging.Message;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.StompSubProtocolErrorHandler;

@Component
@RequiredArgsConstructor
public class DirectMessageStompErrorHandler extends StompSubProtocolErrorHandler {

    private final DirectMessageErrorSender errorSender;

    @Override
    public @Nullable Message<byte[]> handleClientMessageProcessingError(@Nullable Message<byte[]> clientMessage, Throwable ex) {
        if (clientMessage == null){
            return super.handleClientMessageProcessingError(null, ex);
        }

        StompHeaderAccessor headers = StompHeaderAccessor.wrap(clientMessage);
        String sessionId = headers.getSessionId();
        if (sessionId == null || !isDirectMessageFrame(headers)){
            return super.handleClientMessageProcessingError(clientMessage, ex);
        }

        errorSender.sendToSession(sessionId, ex);
        return null;
    }

    private boolean isDirectMessageFrame(StompHeaderAccessor headers){
        StompCommand command = headers.getCommand();
        if (command != StompCommand.SEND && command!= StompCommand.SUBSCRIBE){
            return false;
        }

        String destination = headers.getDestination();
        if (destination == null){
            return false;
        }

        return destination.startsWith("/pub/conversations/")
                || destination.startsWith("/sub/conversations/");
    }
}
