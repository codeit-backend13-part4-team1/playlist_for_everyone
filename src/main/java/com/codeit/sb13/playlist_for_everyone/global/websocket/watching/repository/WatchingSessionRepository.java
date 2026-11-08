package com.codeit.sb13.playlist_for_everyone.global.websocket.watching.repository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

import static com.codeit.sb13.playlist_for_everyone.global.websocket.watching.config.WatchingSessionProperties.*;
import static com.codeit.sb13.playlist_for_everyone.global.websocket.watching.config.WatcherScriptConfig.*;
import static com.codeit.sb13.playlist_for_everyone.global.websocket.watching.WebSocketUtils.check;

/**
 * 세션 - 구독  과 컨텐츠 간 관계 를 레디스에 저장하는 클래스
 * 내부적으론, Hash 와 ZSet 구조를 사용한다.
 * Key [Session] -> Hash[String subscription, String content]]
 * ZSet [Content] -> [Time expiredAt, String session:subscription]
 */

@Slf4j
@Component
public class WatchingSessionRepository {

    private final Duration TTL = Duration.ofSeconds(TTL_TIMEOUT);
    private final StringRedisTemplate redis;

    private final RedisScript<Long> registerScript;
    private final RedisScript<Long> unregisterScript;
    private final RedisScript<Long> refreshScript;
    private final RedisScript<Long> deleteScript;
    private final RedisScript<Long> countScript;
    private final RedisScript<Long> deleteFromContentScript;

    @RequiredArgsConstructor
    static public class SetKey{
        private final String session;
        private final String subscription;

        @Override
        public String toString(){
            return session + DELIMITER + subscription;
        }
    }

    public WatchingSessionRepository(
            StringRedisTemplate redis,
            @Qualifier("lua-script-register")
            RedisScript<Long> registerScript,
            @Qualifier("lua-script-unregister")
            RedisScript<Long> unregisterScript,
            @Qualifier("lua-script-refresh")
            RedisScript<Long> refreshScript,
            @Qualifier("lua-script-delete")
            RedisScript<Long> deleteScript,
            @Qualifier("lua-script-count")
            RedisScript<Long> countScript,
            @Qualifier("lua-script-delete-with-contentId")
            RedisScript<Long> deleteFromContentScript
    ){
        this.redis = redis;
        this.registerScript = registerScript;
        this.unregisterScript = unregisterScript;
        this.refreshScript = refreshScript;
        this.deleteScript = deleteScript;
        this.countScript = countScript;
        this.deleteFromContentScript = deleteFromContentScript;
    }

    /*
    새션 Id 가 겹치는 건, hostname 별로 분간하거나 커넥션 별로 분간 해야 하므로 일단
    모든 세션 id 는 충돌되지 않는다는 가정하에 구현
    분산 인스턴스 간의 충돌 역시 고려하지 않고 구현한다.
     */


    public void register(
            String session,
            String subscription,
            String content
    ){
        // NPE 검사.
        validate(session, subscription, content);

        Long res = redisExecute(registerScript,
                List.of(sessionKey(session), contentKey(content)),
                subscription,
                content,
                new SetKey(session, subscription).toString(),
                String.valueOf(TTL.toMillis())
        );

        if (res == null || Long.valueOf(-1).equals(res))
            throw new IllegalStateException("Failed to register watcher.");
    }

    public void unregister(
            String session,
            String subscription
    ){
        validate(session, subscription);

        Long res = redisExecute(unregisterScript,
                List.of(sessionKey(session)),
                subscription,
                new SetKey(session, subscription).toString(),
                STORAGE_CONTENT_PREFIX
        );


        if (res == null) throw new IllegalStateException("Failed to unregister watcher.");
    }

    public void refresh(String session){
        validate(session);

        Long res = redisExecute(refreshScript,
                List.of(sessionKey(session)),
                session,
                STORAGE_CONTENT_PREFIX,
                String.valueOf(TTL.toMillis()),
                DELIMITER
        );

        if (res == null) throw new IllegalStateException("Failed to refresh watcher.");
    }

    public void delete(String session){
        validate(session);

        Long res = redisExecute(deleteScript,
                List.of(sessionKey(session)),
                session,
                STORAGE_CONTENT_PREFIX,
                DELIMITER
        );

        if (!Long.valueOf(1).equals(res)) throw new IllegalStateException("Failed to delete watcher.");
    }

    public void deleteWithContentId(UUID contentId){
        Long res = redisExecute(
                deleteFromContentScript,
                List.of(contentId.toString()),
                STORAGE_CONTENT_PREFIX,
                STORAGE_SESSION_PREFIX,
                DELIMITER
        );

        if (!Long.valueOf(1).equals(res)) throw new IllegalStateException("Failed to delete watcher.");
    }


    public Long getWatcherCount(String content){
        validate(content);

        return redis.execute(countScript,
                List.of(contentKey(content))
        );
    }

    // 세션 - 구독 쌍에 해당하는 컨텐츠 반환
    public String getContent(String session, String subscription){
        return (String) redis.opsForHash().get(sessionKey(session),subscription);
    }


    // 커스텀 예외를 작성한다면 여기에.
    private void validate(String ...args){
        for (String arg : args){
            check(arg);
        }
    }

    private String sessionKey(String session){
        return STORAGE_SESSION_PREFIX + session;
    }

    private String contentKey(String content){
        return STORAGE_CONTENT_PREFIX + content;
    }


    private Long redisExecute(
            RedisScript<Long> script,
            List<String> keys,
            String ...args
    ){
        return redis.execute(script, keys, (Object[]) args);
    }

}
