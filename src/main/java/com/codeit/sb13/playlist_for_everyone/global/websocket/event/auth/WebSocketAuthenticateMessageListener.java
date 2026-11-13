package com.codeit.sb13.playlist_for_everyone.global.websocket.event.auth;

import com.codeit.sb13.playlist_for_everyone.global.service.redis.RedisMessageListener;
import com.codeit.sb13.playlist_for_everyone.global.websocket.service.WebSocketService;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.GenericJacksonJsonRedisSerializer;
import org.springframework.stereotype.Component;

@Component
public class WebSocketAuthenticateMessageListener extends RedisMessageListener {

    private final WebSocketService webSocketService;
    private final String topic = "websocket:event:auth:revoke";

    public WebSocketAuthenticateMessageListener(
            RedisTemplate<String, Object> template,
            @Qualifier("redisSerializer")GenericJacksonJsonRedisSerializer serializer,
            WebSocketService webSocketService
    ){
        super(template, serializer);
        this.webSocketService = webSocketService;
    }

    @Override
    public void run(Object data){
        String username = (String) data;
        webSocketService.revokeAuth(username);
    }

    public String topic(){
        return topic;
    }

}
