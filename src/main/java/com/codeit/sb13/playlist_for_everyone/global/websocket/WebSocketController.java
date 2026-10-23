package com.codeit.sb13.playlist_for_everyone.global.websocket;


import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.annotation.SubscribeMapping;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.stereotype.Component;

import java.security.Principal;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class WebSocketController {

    private final SimpMessagingTemplate message;

    // 초기 구독시 WatchingSessionDto 한번 반환.
    @SubscribeMapping("/sub/contents/{contentId}/watch")
    public void contentWatch(
            @DestinationVariable UUID contentId,
            StompHeaderAccessor header,
            Principal principal
    ){
        // 컨텐츠, 유저 서비스 이용해서 WatcherSessionDto 반환.
        message.convertAndSend("/sub/contents/" + contentId + "/watch", principal);
    }

    @MessageMapping("/pub/contents/{contentId}/chat")
    public void contentChatReceive(
            @DestinationVariable UUID contentId,
            StompHeaderAccessor header,
            Principal principal
    ){}

}
