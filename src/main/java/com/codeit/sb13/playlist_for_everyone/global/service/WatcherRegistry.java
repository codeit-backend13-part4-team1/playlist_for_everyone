package com.codeit.sb13.playlist_for_everyone.global.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.UUID;
import java.util.function.BiConsumer;

import static com.codeit.sb13.playlist_for_everyone.global.service.RedisLockProvider.*;
import static com.codeit.sb13.playlist_for_everyone.global.config.RedisConfig.*;

@Slf4j
@Component
@RequiredArgsConstructor
public class WatcherRegistry {

    private final StringRedisTemplate redis;
    private final RedisLockProvider lockProvider;

    /**
     * 방(컨텐츠) 를 구독(시청)하고있는 현재 사용자 수 반환.
     * @param roomId - 시청하고있는 컨텐츠 id.
     * @return 현재 시청하고있는 시청자의 수. 방이 없다면 0 명.
     */
    public long getWatcherCount(String roomId){
        return redis.opsForSet().size(contentKey(roomId));
    }

    /**
     * 세션 id 를 통해 고유한 시청자를 등록체에 주입한다.
     * @param roomId 컨텐츠 id
     * @param session 세션 id
     */
    public void register(String roomId, String session){
        lockProxy(
                roomId,
                session,
                (contentId,sessionId)->{
                    redis.opsForSet().add(contentKey(contentId), sessionId);
                    redis.opsForSet().add(sessionKey(sessionId), contentId);
                });
    }

    /**
     * 세션 id 를 통해 고유한 시청자를 등록에서 삭제한다.
     * @param session 세션 id
     */
    public void unregister(String session){
        lockProxy(
                null,
                session,
                (noUse,sessionId)->{
                    Set<String> contentIdList = redis.opsForSet().members(sessionKey(sessionId));
                    for (String contentId : contentIdList){
                        redis.opsForSet().remove(contentKey(contentId), sessionId);
                        redis.opsForSet().remove(sessionKey(sessionId), contentId);
                    }
                });
    }

    private void lockProxy(String roomId, String sessionId,BiConsumer<String,String> function){
        String lockKey = UUID.randomUUID().toString();
        String lockId = null;
        try {
            lockId = lockProvider.lock(lockKey);
            function.accept(roomId,sessionId);
        } catch (RedisLockException e){
            log.error("[WatcherRegistry] 컨텐츠 세션 저장 에러! - contentId: {}, sessionId: {}", roomId, sessionId, e);
            // todo - 적절한 예외를 생성해서 대체
            throw new RuntimeException(e);
        }
        finally {
            lockProvider.unlock(lockKey, lockId);
        }
    }

    private String sessionKey(String sessionId){ return WATCHER_SESSION_PREFIX + sessionId; }
    private String contentKey(String contentId){ return WATCHER_CONTENT_PREFIX + contentId; }
}
