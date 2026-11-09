package com.codeit.sb13.playlist_for_everyone.domain.auth.repository.impl;

import com.codeit.sb13.playlist_for_everyone.domain.auth.repository.SessionStartResult;
import com.codeit.sb13.playlist_for_everyone.domain.auth.repository.UserSessionRepository;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

import java.time.Duration;
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

    private final StringRedisTemplate redisTemplate;

    public RedisUserSessionRepository(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public SessionStartResult start(UUID userId, Duration ttl) {
        throw new UnsupportedOperationException("구현 예정");
    }

    @Override
    public boolean isCurrent(UUID userId, UUID sessionId) {
        throw new UnsupportedOperationException("구현 예정");
    }

    @Override
    public boolean extendIfCurrent(UUID userId, UUID sessionId, Duration ttl) {
        throw new UnsupportedOperationException("구현 예정");
    }

    @Override
    public void endIfCurrent(UUID userId, UUID sessionId) {
        throw new UnsupportedOperationException("구현 예정");
    }

    @Override
    public Optional<UUID> revoke(UUID userId) {
        throw new UnsupportedOperationException("구현 예정");
    }
}
