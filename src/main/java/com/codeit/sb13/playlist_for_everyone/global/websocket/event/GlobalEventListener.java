package com.codeit.sb13.playlist_for_everyone.global.websocket.event;

import com.codeit.sb13.playlist_for_everyone.domain.auth.event.UserSessionRevokedEvent;
import com.codeit.sb13.playlist_for_everyone.global.service.redis.RedisBrokerRegister;
import com.codeit.sb13.playlist_for_everyone.global.websocket.event.auth.WebSocketAuthenticateMessageListener;
import com.codeit.sb13.playlist_for_everyone.global.websocket.event.auth.WebSocketAuthenticateRevokeEvent;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.event.EventListener;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@ConditionalOnProperty(name = "everyone.websocket.event.multiple", havingValue = "true")
@RequiredArgsConstructor
public class GlobalEventListener {

    private final RedisTemplate<String, Object> redisTemplate;
    private final RedisBrokerRegister broker;
    private final WebSocketAuthenticateMessageListener authListener;

    @PostConstruct
    public void setup(){
        broker.register(authListener, authListener.topic());
    }


    @EventListener
    public void onAuthRevoke(UserSessionRevokedEvent event){
        redisTemplate.convertAndSend(authListener.topic(), event.userId());
    }

}
