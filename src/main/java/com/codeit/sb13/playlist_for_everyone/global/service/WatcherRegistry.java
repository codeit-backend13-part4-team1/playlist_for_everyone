package com.codeit.sb13.playlist_for_everyone.global.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;

import static com.codeit.sb13.playlist_for_everyone.global.service.RedisLockProvider.*;
import static com.codeit.sb13.playlist_for_everyone.global.config.RedisConfig.*;

@Slf4j
@Component
@RequiredArgsConstructor
public class WatcherRegistry {

    private final StringRedisTemplate redis;
    private final RedisLockProvider lockProvider;

    private final ConcurrentHashMap<String, Set<String>> contentIdListPerSession = new ConcurrentHashMap<>();

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
        try {
            lockProxy(
                    roomId,
                    session,
                    (contentId,sessionId) -> redis.opsForSet().add(contentKey(contentId), sessionId));
            contentIdListPerSession.computeIfAbsent(session,k -> ConcurrentHashMap.newKeySet()).add(roomId);
        } catch (RuntimeException e) {
            log.error("[WatcherRegistry] 시청 세션({}) 저장 에러 - contentId : {} ", session, roomId, e);
        }
    }

    /**
     * 세션 id 를 통해 고유한 시청자를 등록에서 삭제한다.
     * @param session 세션 id
     */
    public void unregister(String session){
        Set<String> contents = contentIdListPerSession.get(session);

        // 등록된 컨텐츠가 없다면 패스
        if (contents == null || contents.isEmpty()) return;

        // 순회시 인덱스 변경 방지를 위한 copy
        for(String content : Set.copyOf(contents)){
            try {
                lockProxy(
                        content,
                        session,
                        (c,s) -> redis.opsForSet().remove(contentKey(c), s)
                );

                contents.remove(content);
            } catch (RuntimeException e) {
                log.error("[WatcherRegistry] 시청 세션({}) 삭제 에러 - Content : {}", session, content, e);
            }

            // 비었으면 Set 삭제
            if (contents.isEmpty()) contentIdListPerSession.remove(session);
        }

    }

    private void lockProxy(String roomId, String sessionId, BiConsumer<String,String> function){
        String lockKey = roomId;  // contents 단위로 락이 걸려야 함
        String lockId = null;

        try {
            lockId = lockProvider.lock(lockKey);
            function.accept(roomId,sessionId);
        } catch (RedisLockException e){
            throw new RuntimeException(e);
        }
        finally {
            if (lockId != null)
                lockProvider.unlock(lockKey, lockId);
        }
    }

    private String contentKey(String contentId){ return WATCHER_CONTENT_PREFIX + contentId; }
}
