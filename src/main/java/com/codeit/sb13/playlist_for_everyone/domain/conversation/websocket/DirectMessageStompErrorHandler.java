package com.codeit.sb13.playlist_for_everyone.domain.conversation.websocket;

import com.codeit.sb13.playlist_for_everyone.domain.conversation.service.DirectMessageErrorSender;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.StompSubProtocolErrorHandler;

@Component
@RequiredArgsConstructor
public class DirectMessageStompErrorHandler extends StompSubProtocolErrorHandler {

    private final DirectMessageErrorSender errorSender;

}
