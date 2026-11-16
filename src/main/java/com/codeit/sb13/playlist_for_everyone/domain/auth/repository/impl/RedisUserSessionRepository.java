package com.codeit.sb13.playlist_for_everyone.domain.auth.repository.impl;

import com.codeit.sb13.playlist_for_everyone.domain.auth.repository.SessionStartResult;
import com.codeit.sb13.playlist_for_everyone.domain.auth.repository.UserSessionRepository;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Repository;

import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;


/**
 * UserSessionRepository의 Redis 구현체.
 *
 * Key: auth:session:<사용자 ID>
 * Value: 현재 로그인 세션 ID
 * TTL: Refresh Token 유효기간. 재발급 시 연장한다.
 */
@Repository
public class RedisUserSessionRepository implements UserSessionRepository {

    private static final String KEY_PREFIX = "auth:session:";

    /**
     * 이전 세션 ID를 조회하고 새 세션 ID로 교체한다.
     * <p>
     * KEYS[1]: 사용자 세션 키
     * ARGV[1]: 새 세션 ID
     * ARGV[2]: TTL(밀리초)
     * <p>
     * 반환값: 이전 세션 ID, 없으면 nil
     */
    private static final DefaultRedisScript<String> REPLACE_SCRIPT =
            new DefaultRedisScript<>("""
                    -- 조회와 교체 사이에 다른 로그인이 끼어들지 않도록 한 번에 처리한다.
                    local previous = redis.call('GET', KEYS[1])
                    redis.call('SET', KEYS[1], ARGV[1], 'PX', ARGV[2])
                    return previous
                    """, String.class);


    /**
     * 현재 세션일 때만 TTL을 연장한다
     * KEYS[1]: 사용자 세션 키
     * ARGV[1]: 확인할 세션 ID
     * ARGV[2]: TTL(밀리초)
     * <p>
     * 반환값: 연장 성공 1, 현재 세션이 아니면 0
     */
    private static final DefaultRedisScript<Long> EXTEND_IF_CURRENT_SCRIPT =
            new DefaultRedisScript<>("""
                    -- 비교 후 연장 사이에 새 로그인이 일어나면
                    -- 새 세션의 TTL을 이전 세션 기준으로 바꿀 수 있으므로 한 번에 처리한다.
                    if redis.call('GET', KEYS[1]) == ARGV[1] then
                        return redis.call('PEXPIRE', KEYS[1], ARGV[2])
                    end
                    return 0
                    """, Long.class);

    /**
     * 현재 세션일 때만 삭제한다.
     *
     * KEYS[1]: 사용자 세션 키
     * ARGV[1]: 확인할 세션 ID
     *
     * 반환값: 삭제 성공 1, 현재 세션이 아니면 0
     */
    private static final DefaultRedisScript<Long> DELETE_IF_CURRENT_SCRIPT =
            new DefaultRedisScript<>("""
                -- 비교 후 삭제 사이에 새 로그인이 일어나면
                -- 새 세션을 지울 수 있으므로 한 번에 처리한다.
                if redis.call('GET', KEYS[1]) == ARGV[1] then
                    return redis.call('DEL', KEYS[1])
                end
                return 0
                """, Long.class);

    private final StringRedisTemplate redisTemplate;

    public RedisUserSessionRepository(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public SessionStartResult start(UUID userId, Duration ttl) {
        Objects.requireNonNull(userId, "사용자 ID는 필수입니다.");
        long ttlMillis = toMillis(ttl);

        UUID sessionId = UUID.randomUUID();

        String previous = redisTemplate.execute(
                REPLACE_SCRIPT,
                List.of(redisKey(userId)),
                sessionId.toString(),
                Long.toString(ttlMillis)
        );

        return new SessionStartResult(
                sessionId,
                previous == null ? null : UUID.fromString(previous)
        );

    }

    @Override
    public boolean isCurrent(UUID userId, UUID sessionId) {
        // 필터에서 요청마다 호출하므로 예외 대신 false로 거부한다.
        if (userId == null || sessionId == null) {
            return false;
        }

        String current = redisTemplate.opsForValue().get(redisKey(userId));

        return sessionId.toString().equals(current);
    }


    @Override
    public boolean extendIfCurrent(UUID userId, UUID sessionId, Duration ttl) {
        Objects.requireNonNull(userId, "사용자 ID는 필수입니다.");
        Objects.requireNonNull(sessionId, "세션 ID는 필수입니다.");
        long ttlMillis = toMillis(ttl);

        Long result = redisTemplate.execute(
                EXTEND_IF_CURRENT_SCRIPT,
                List.of(redisKey(userId)),
                sessionId.toString(),
                Long.toString(ttlMillis)
        );
        return Long.valueOf(1L).equals(result);
    }

    @Override
    public void endIfCurrent(UUID userId, UUID sessionId) {
        Objects.requireNonNull(userId, "사용자 ID는 필수입니다.");
        Objects.requireNonNull(sessionId, "세션 ID는 필수입니다.");

        redisTemplate.execute(
                DELETE_IF_CURRENT_SCRIPT,
                List.of(redisKey(userId)),
                sessionId.toString()
        );
    }

    @Override
    public Optional<UUID> revoke(UUID userId) {
        Objects.requireNonNull(userId, "사용자 ID는 필수입니다.");

        String revoked = redisTemplate.opsForValue().getAndDelete(redisKey(userId));

        return Optional.ofNullable(revoked).map(UUID::fromString);
    }

    private String redisKey(UUID userId) {
        return KEY_PREFIX + userId;
    }

    /**
     * Redis의 PX 옵션에 전달할 밀리초로 변환한다.
     * Refresh Token 저장소와 같은 기준으로 검증한다.
     */
    private long toMillis(Duration ttl) {
        Objects.requireNonNull(ttl, "세션 유효기간은 필수입니다");

        if (ttl.isZero() || ttl.isNegative()) {
            throw new IllegalArgumentException("세션 유효기간은 0보다 커야 합니다.");
        }

        long ttlMillis = ttl.toMillis();

        if (ttlMillis <= 0) {
            throw new IllegalArgumentException("세션 유효기간은 1밀리초 이상이어야 합니다.");
        }

        return ttlMillis;
    }
}
