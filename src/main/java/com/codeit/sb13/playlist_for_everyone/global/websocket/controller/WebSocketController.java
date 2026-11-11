package com.codeit.sb13.playlist_for_everyone.global.websocket.controller;


import com.codeit.sb13.playlist_for_everyone.global.websocket.SessionInfo;
import com.codeit.sb13.playlist_for_everyone.global.websocket.watching.dto.chat.ContentChatSendRequest;
import com.codeit.sb13.playlist_for_everyone.global.websocket.watching.event.WatchingChatSendEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.messaging.Message;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;

@Slf4j
@Controller
@RequiredArgsConstructor
public class WebSocketController {

    private final ApplicationEventPublisher eventPublisher;
    @MessageMapping("/contents/{contentId}/chat")
    @PreAuthorize("hasAnyRole('USER','ADMIN') && authentication.principal.enabled")
    public void contentChatReceive(
            Message<?> message,
            ContentChatSendRequest payload
    ){
        // Send 는 구독id 가 없고, 세션과 destination 만 존재한다.
        SessionInfo info = new SessionInfo(message);
        log.info("contentChatReceive : {}", payload);
        eventPublisher.publishEvent(
                new WatchingChatSendEvent(
                        info,
                        payload.content()
                )
        );
    }

}
