package com.codeit.sb13.playlist_for_everyone.global.websocket.event;

import com.codeit.sb13.playlist_for_everyone.global.websocket.registry.session.WebSocketRegistry;
import com.codeit.sb13.playlist_for_everyone.global.websocket.watching.WatchingSession;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.GenericJacksonJsonRedisSerializer;
import org.springframework.messaging.simp.user.SimpUser;
import org.springframework.messaging.simp.user.SimpUserRegistry;
import org.springframework.stereotype.Component;

/**
 * 유저 로그인 상태가 변경 된 경우, 해당 유저의 인증정보로 로그인 된 모든 웹 소켓 세션을
 * 강제로 종료한다.
 */

@Slf4j
@Component
public class WebSocketAuthenticateRevokeEvent extends RedisWebSocketEvent{
    private final SimpUserRegistry userRegistry;
    private final WebSocketRegistry webSocketRegistry;
    private final WatchingSession watchingSession; // 리팩토링 이후 안 쓸 가능성 높음.

    public WebSocketAuthenticateRevokeEvent(
            RedisTemplate<String, Object> redisTemplate,
            @Qualifier("redisSerializer") GenericJacksonJsonRedisSerializer serializer,
            SimpUserRegistry userRegistry,
            WebSocketRegistry webSocketRegistry,
            WatchingSession watchingSession
    ) {
        super(redisTemplate, serializer);
        this.userRegistry = userRegistry;
        this.webSocketRegistry = webSocketRegistry;
        this.watchingSession = watchingSession;

    }

    @Override
    public void run(Object data) {
        SimpUser user = userRegistry.getUser((String) data);

        if (user != null)
            user.getSessions().forEach(session -> {
                String sessionId = session.getId();
            watchingSession.disconnectWithSessionIds(sessionId);
                try {
                    webSocketRegistry.close(sessionId);
                } catch (Exception e) {
                    log.error("[WebSocketAuthRevokeEvent] 세션 종료에 실패 했습니다. 세션ID : {}", sessionId);
                }
            });

    }

}
