package com.codeit.sb13.playlist_for_everyone.global.websocket.watching;

import com.codeit.sb13.playlist_for_everyone.global.service.redis.RedisMessageDto;
import com.codeit.sb13.playlist_for_everyone.global.service.redis.RedisMessageListener;
import com.codeit.sb13.playlist_for_everyone.global.websocket.watching.dto.watcher.WatchingSessionChange;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

import java.util.Objects;

/**
 * 현재 시청 수 관련 redis 메세지를 처리하는 리스너.
 * destination 을 통해 같은 컨텐츠를 구독하는 채널에 데이터 전송.
 * 매 구독마다 새로 만들거면 scope 변경.
 */

@Slf4j
@Component
public class WatchingSessionMessageListener extends RedisMessageListener {

    private final SimpMessagingTemplate message;

    public WatchingSessionMessageListener(
            RedisTemplate<String, Object> redisTemplate,
            SimpMessagingTemplate simpMessagingTemplate
    ) {
        super(redisTemplate);
        message = simpMessagingTemplate;
    }

    @Override
    protected void run(Object data) {
        RedisMessageDto<WatchingSessionChange> dto =
                (RedisMessageDto<WatchingSessionChange>) Objects.requireNonNull(data);
        // 메세지 발신(요 수정)
        message.convertAndSend("/topic/watching-session/" + dto.destination(), dto.data());
    }

}
