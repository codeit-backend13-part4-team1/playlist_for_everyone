package com.codeit.sb13.playlist_for_everyone.global.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.Set;
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
        String lockKey = roomId;  // contents 단위로 락이 걸려야 함
        String lockId = null;

        // contents 단위로 락을 걸기 위해 session 만 남은 경우(disconnect) 는 session 에서 content 추출
        // 기능상 한 세션에 하나의 공통채널 구독(한 페이지당 하나의 채널 시청 가능) 만 존재.
        // 만약 하나의 세션으로 여러개의 동시시청 채널을 구독한다면, 반복문으로 세션에 걸린 모든 채널 락?
        if (roomId == null) {
            // 이부분이 동시성 문제가 생긴다. 어떻게 해결할지 고민.
            // 그냥 로컬에 세션 - 컨텐츠 키를 저장하고, disconnect 시에는 해당 키를 없애면 될까?
            Set<String> channelIdListPerSession = redis.opsForSet().members(sessionKey(sessionId));
            lockKey = channelIdListPerSession.stream().findFirst().orElse(null);
            if (lockKey == null) return; // NPE 방지. 키가 없다면 진행 안한다.
        }

        try {
            lockId = lockProvider.lock(lockKey);
            function.accept(roomId,sessionId);
        } catch (RedisLockException e){
            log.error("[WatcherRegistry] 컨텐츠 세션 저장 에러! - contentId: {}, sessionId: {}", roomId, sessionId, e);
            // todo - 적절한 예외를 생성해서 대체
            throw new RuntimeException(e);
        }
        finally {
            if (lockId != null)
                lockProvider.unlock(lockKey, lockId);
        }
    }

    private String sessionKey(String sessionId){ return WATCHER_SESSION_PREFIX + sessionId; }
    private String contentKey(String contentId){ return WATCHER_CONTENT_PREFIX + contentId; }
}
