package com.codeit.sb13.playlist_for_everyone.global.websocket.watching.event;

import com.codeit.sb13.playlist_for_everyone.global.service.redis.RedisMessageListener;
import com.codeit.sb13.playlist_for_everyone.global.websocket.watching.WatchingSession;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.GenericJacksonJsonRedisSerializer;
import org.springframework.stereotype.Component;

import java.util.Objects;
import java.util.UUID;

@Component
public class WatchingSessionContentDeleteEventListener extends RedisMessageListener {

    private final WatchingSession watchingSession;
    private final String topic = "websocket:event:watching:delete";

    public WatchingSessionContentDeleteEventListener(
            RedisTemplate<String, Object> template,
            @Qualifier("redisSerializer") GenericJacksonJsonRedisSerializer serializer,
            WatchingSession watchingSession
    ){
        super(template, serializer);
        this.watchingSession = watchingSession;
    }

    @Override
    public void run(Object data){
        UUID contentId = UUID.fromString(Objects.requireNonNull((String) data));
        watchingSession.disconnectWithContentId(contentId);
    }

    public String topic(){ return topic; }

}
