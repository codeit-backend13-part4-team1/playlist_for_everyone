package com.codeit.sb13.playlist_for_everyone.domain.auth.repository.impl;

import com.codeit.sb13.playlist_for_everyone.domain.auth.repository.RefreshTokenRepository;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

/**
 * RefreshTokenRepository의 Redis 구현체
 *
 * 토큰 원문 대신 SHA-256 해시를 키에 사용하고,
 * 사용자 ID와 TTL을 저장한다.
 */

@Repository
public class RedisRefreshTokenRepository implements RefreshTokenRepository {

    private final StringRedisTemplate redisTemplate;

    public RedisRefreshTokenRepository(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public void save(UUID userId, String refreshToken, Duration ttl) {
        throw new UnsupportedOperationException("Refresh Token 저장 미구현");
    }

    @Override
    public Optional<UUID> findUserId(String refreshToken) {
        throw new UnsupportedOperationException("Refresh Token 조회 미구현");
    }

    @Override
    public void delete(String refreshToken) {
        throw new UnsupportedOperationException("Refresh Token 삭제 미구현");
    }
}
