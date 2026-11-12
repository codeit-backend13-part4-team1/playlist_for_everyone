package com.codeit.sb13.playlist_for_everyone.global.websocket.watching.event;

import com.codeit.sb13.playlist_for_everyone.global.websocket.event.RedisWebSocketEvent;
import com.codeit.sb13.playlist_for_everyone.global.websocket.watching.WatchingSession;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.GenericJacksonJsonRedisSerializer;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Slf4j
@Component
public class WatchingSessionContentDeletedEvent extends RedisWebSocketEvent{
    private final WatchingSession watchingSession;


    public WatchingSessionContentDeletedEvent(
            RedisTemplate<String, Object> redisTemplate,
            @Qualifier("redisSerializer") GenericJacksonJsonRedisSerializer serializer,
            WatchingSession watchingSession
    ) {
        super(redisTemplate, serializer);
        this.watchingSession = watchingSession;
    }

    @Override
    public void run(Object data){
        UUID contentId = UUID.fromString((String) data);
        watchingSession.disconnectWithContentId(contentId);
    }

}
