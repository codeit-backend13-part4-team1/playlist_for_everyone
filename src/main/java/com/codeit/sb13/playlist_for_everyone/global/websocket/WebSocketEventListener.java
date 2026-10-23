package com.codeit.sb13.playlist_for_everyone.global.websocket;


import com.codeit.sb13.playlist_for_everyone.global.service.WatcherRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

@Slf4j
@Component
@RequiredArgsConstructor
public class WebSocketEventListener {

    private final WatcherRegistry watcherRegistry;

    @EventListener
    public void onDisconnect(SessionDisconnectEvent event){
        String sessionId = event.getSessionId();

        log.debug("[WebSocketEvent] socket disconnect. - {}", sessionId);

        watcherRegistry.unregister(sessionId);
    }



}
