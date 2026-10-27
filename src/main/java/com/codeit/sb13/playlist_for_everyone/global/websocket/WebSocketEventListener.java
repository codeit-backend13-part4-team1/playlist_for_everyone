package com.codeit.sb13.playlist_for_everyone.global.websocket;


import com.codeit.sb13.playlist_for_everyone.global.service.WatcherRegistry;
import com.codeit.sb13.playlist_for_everyone.global.websocket.dto.chat.ContentChatDto;
import com.codeit.sb13.playlist_for_everyone.global.websocket.dto.event.WebSocketChatEvent;
import com.codeit.sb13.playlist_for_everyone.global.websocket.dto.event.WebSocketContentWatchEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;
import org.springframework.web.socket.messaging.SessionUnsubscribeEvent;

import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class WebSocketEventListener {

    private final WatcherRegistry watcherRegistry;


    private final SimpMessagingTemplate messagingTemplate;

    static private final String SUB_PREFIX = "/sub/contents/";
    static private final String CHAT_POSTFIX = "/chat";
    static private final String WATCH_POSTFIX = "/watch";

    // 세션단위 연결이 끊겼을 때.
    @EventListener
    public void onDisconnect(SessionDisconnectEvent event){
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(event.getMessage());
        String sessionId = accessor.getSessionId();
        String subscriptionId = accessor.getSubscriptionId();

        watcherRegistry.unregister(sessionId, subscriptionId);
    }

    // Stomp 구독단위 연결이 끊겼을 때.
    @EventListener
    public void onUnsubscribe(SessionUnsubscribeEvent event){
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(event.getMessage());
        String sessionId = accessor.getSessionId();
        String subscriptionId = accessor.getSubscriptionId();

        watcherRegistry.unregister(sessionId, subscriptionId);
    }



    @EventListener
    public void onWatch(WebSocketContentWatchEvent event){
        messagingTemplate.convertAndSend(
                pathResolve(SUB_PREFIX, event.contentId(), WATCH_POSTFIX)
                , event.content());
    }

    @EventListener
    public void onChat(WebSocketChatEvent event){

        // 메세지 전송 로직
        ContentChatDto chatDto = new ContentChatDto(event.user(), event.message());
        messagingTemplate.convertAndSend(
                pathResolve(SUB_PREFIX, event.contentId(), CHAT_POSTFIX),
                chatDto);
    }

    private String pathResolve(String prefix, UUID contentId, String postfix){
        return prefix + contentId + postfix ;
    }


}
