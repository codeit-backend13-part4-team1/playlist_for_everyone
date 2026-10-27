package com.codeit.sb13.playlist_for_everyone.global.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;
import java.util.Objects;

import static com.codeit.sb13.playlist_for_everyone.global.config.RedisConfig.*;

@Slf4j
@Component
public class WatcherRegistry {

    static private final Duration TTL = Duration.ofSeconds(60);
    private final StringRedisTemplate redis;

    private final RedisScript<Long> registerScript;
    private final RedisScript<Long> unregisterScript;
    private final RedisScript<Long> refreshScript;
    private final RedisScript<Long> deleteScript;
    private final RedisScript<Long> countScript;

    @RequiredArgsConstructor
    static public class SetKey{

        static private final String DELIMITER = "-";

        private final String session;
        private final String subscription;

        @Override
        public String toString(){
            return session + DELIMITER + subscription;
        }
    }

    public WatcherRegistry(
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
            RedisScript<Long> countScript
    ){
        this.redis = redis;
        this.registerScript = registerScript;
        this.unregisterScript = unregisterScript;
        this.refreshScript = refreshScript;
        this.deleteScript = deleteScript;
        this.countScript = countScript;
    }

    public void register(
            String session,
            String subscription,
            String content
    ){
        // NPE 검사.
        validate(session, subscription, content);

        Long result = redis.execute(registerScript,
                List.of(sessionKey(session), contentKey(content)),
                subscription,
                content,
                new SetKey(session, subscription).toString(),
                String.valueOf(TTL.toMillis())
        );

        if (!Long.valueOf(1).equals(result)) throw new IllegalStateException("Failed to register watcher.");
    }

    public void unregister(
            String session,
            String subscription
    ){
        validate(session, subscription);

        Long res = redis.execute(unregisterScript,
                List.of(sessionKey(session)),
                subscription,
                new SetKey(session, subscription).toString(),
                WATCHER_CONTENT_PREFIX
        );

        if (!Long.valueOf(1).equals(res)) throw new IllegalStateException("Failed to unregister watcher.");
    }

    public void refresh(String session){
        validate(session);

        Long res = redis.execute(refreshScript,
                List.of(sessionKey(session)),
                session,
                WATCHER_CONTENT_PREFIX,
                String.valueOf(TTL.toMillis()),
                "-"
        );
        log.info("[WatcherRegistry] Refreshed {}", res);

        if (!Long.valueOf(1).equals(res)) throw new IllegalStateException("Failed to refresh watcher.");
    }

    public void delete(String session){
        validate(session);

        Long res = redis.execute(deleteScript,
                List.of(sessionKey(session)),
                session,
                WATCHER_CONTENT_PREFIX,
                "-"
        );

        if (!Long.valueOf(1).equals(res)) throw new IllegalStateException("Failed to delete watcher.");
    }


    public Long getWatcherCount(String content){
        validate(content);

        return redis.execute(countScript,
                List.of(contentKey(content))
        );
    }


    // 커스텀 예외를 작성한다면 여기에.
    private void validate(String ...args){
        for (String arg : args){
            Objects.requireNonNull(arg);
        }
    }

    private String sessionKey(String session){
        return WEBSOCKET_SESSION_PREFIX + session;
    }

    private String contentKey(String content){
        return WATCHER_CONTENT_PREFIX + content;
    }
}
