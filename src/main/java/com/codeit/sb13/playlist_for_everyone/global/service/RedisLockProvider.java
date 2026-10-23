package com.codeit.sb13.playlist_for_everyone.global.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Collections;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class RedisLockProvider {

    static private final Duration LOCK_TIMEOUT = Duration.ofSeconds(3);
    static private final String LOCK_KEY_PREFIX = "lock:";

    private final StringRedisTemplate redisTemplate;

    static private final DefaultRedisScript<Long> UNLOCK_SCRIPT = new DefaultRedisScript<>();

    static {
        // redis.call(...) - 레디스 내부 스크립트로 사용
        // "GET", KEY[1] - 첫번째 인자를 키로, 해당 키 값을 가져옴
        // == ARGV[1] 반환된 값이 두번째(첫번째는 키 이므로) 인자와 같음
        UNLOCK_SCRIPT.setScriptText("""
                if redis.call('GET',KEYS[1]) == ARGV[1] then
                    return redis.call('DEL',KEYS[1])
                else 
                    return 0
                end
                """);

        UNLOCK_SCRIPT.setResultType(Long.class);    // 성공하면 1L 실패하면 0L
    }


    public static class RedisLockException extends RuntimeException {
        public RedisLockException(String message) {
            super(message);
        }
    }

    public String lock(String key){
        String lockKey = LOCK_KEY_PREFIX + key;
        String lockValue = UUID.randomUUID().toString();
        ValueOperations<String, String> ops = redisTemplate.opsForValue();

        Boolean locked = ops.setIfAbsent(lockKey, lockValue, LOCK_TIMEOUT);

        if (Boolean.TRUE.equals(locked)) {
            log.debug("[Redis] 분산 락 획득 - key: {}, value : {}", lockKey, lockValue);
            return lockValue;
        }
        else {
            log.error("[Redis] 분산 락 획득 실패 - key: {}, value : {}", lockKey, lockValue);
            throw new RedisLockException("redis locking failure");
        }
    }

    public void unlock(String key, String id){
        if (id == null) return;
        String lockKey = LOCK_KEY_PREFIX + key;
        try {
            Long result = redisTemplate.execute(
                    UNLOCK_SCRIPT,
                    Collections.singletonList(lockKey), // 스크립트에 사용할 키 인자
                    id); // 스크립트에 사용할 값 인자들 -> lock 이 반환한 식별자
            if (Long.valueOf(1L).equals(result))
                log.debug("[Redis] 분산락 해제 성공 - lockKey: {}, id : {}", lockKey, id);
            else
                log.warn("[Redis] 분산락 해제 실패 - lockKey: {}, id : {}", lockKey, id);
        } catch (Exception e) {
            log.error("[Redis] 분산 락 해제 에러! - key : {}, id : {}", key, id, e);
        }
    }



}
