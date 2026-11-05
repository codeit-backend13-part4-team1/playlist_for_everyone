package com.codeit.sb13.playlist_for_everyone.global.websocket;

import com.codeit.sb13.playlist_for_everyone.global.service.redis.RedisMessageDto;
import com.codeit.sb13.playlist_for_everyone.global.service.redis.RedisMessageListener;
import com.codeit.sb13.playlist_for_everyone.global.websocket.watching.dto.watcher.WatchingSessionChange;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.GenericJacksonJsonRedisSerializer;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

import java.util.Objects;

@Component
public class ViewSessionMessageListener extends RedisMessageListener {

    private final SimpMessagingTemplate message;

    public ViewSessionMessageListener(
            RedisTemplate<String, Object> redisTemplate,
            @Qualifier("redisSerializer") GenericJacksonJsonRedisSerializer serializer,
            SimpMessagingTemplate simpMessagingTemplate
    ) {
        super(redisTemplate, serializer);
        this.message = simpMessagingTemplate;
    }

    /**
     * 직렬화 되어 온 데이터 data 에 존재하는
     * Destination
     * @param data 역직렬화 된 데이터
     */

    @Override
    public void run(Object data){
        RedisMessageDto<WatchingSessionChange> dto =
                (RedisMessageDto<WatchingSessionChange>) Objects.requireNonNull(data);

        // 경로는 발신 경로 그대로.
        String destination = dto.destination();
        WatchingSessionChange change = dto.data();

        // 메세지로 발신.
        message.convertAndSend(destination, change);
    }

}
