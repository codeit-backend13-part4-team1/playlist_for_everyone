package com.codeit.sb13.playlist_for_everyone.global.websocket;


import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;
import org.springframework.web.socket.messaging.SessionUnsubscribeEvent;

@Slf4j
@Component
@RequiredArgsConstructor
public class WebSocketEventListener {

    // 세션단위 연결이 끊겼을 때.
    @EventListener
    public void onDisconnect(SessionDisconnectEvent event){
    }

    // Stomp 구독단위 연결이 끊겼을 때.
    @EventListener
    public void onUnsubscribe(SessionUnsubscribeEvent event){
    }

}
