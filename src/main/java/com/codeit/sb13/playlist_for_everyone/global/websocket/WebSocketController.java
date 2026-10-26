package com.codeit.sb13.playlist_for_everyone.global.websocket;


import com.codeit.sb13.playlist_for_everyone.global.websocket.dto.chat.ContentChatSendRequest;
import com.codeit.sb13.playlist_for_everyone.global.websocket.dto.event.WebSocketChatEvent;
import com.codeit.sb13.playlist_for_everyone.global.websocket.dto.event.WebSocketContentWatchEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.annotation.SubscribeMapping;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.stereotype.Controller;

import java.security.Principal;
import java.util.UUID;

@Slf4j
@Controller
@RequiredArgsConstructor
public class WebSocketController {

    private final ApplicationEventPublisher eventPublisher;

    // 초기 구독시 WatchingSessionDto 한번 반환.
    @SubscribeMapping("/contents/{contentId}/watch")
    public void contentWatch(
            @DestinationVariable UUID contentId,
            StompHeaderAccessor header,
            Principal principal
    ){
        // 추후 컨텐츠, 유저 서비스 이용해서 WatcherSessionDto 반환.
        return;
//        eventPublisher.publishEvent(new WebSocketContentWatchEvent(contentId, null));
    }


    // todo - userSummary 객체가 정의/User 도메인이 어느정도 만들어 지면 user 필드를 userSummary 로 교체
    @MessageMapping("/contents/{contentId}/chat")
    public void contentChatReceive(
            @DestinationVariable UUID contentId,
            StompHeaderAccessor header,
            Principal principal,
            ContentChatSendRequest message
    ){
        eventPublisher.publishEvent(new WebSocketChatEvent(contentId, null, message.content()));
    }

}
