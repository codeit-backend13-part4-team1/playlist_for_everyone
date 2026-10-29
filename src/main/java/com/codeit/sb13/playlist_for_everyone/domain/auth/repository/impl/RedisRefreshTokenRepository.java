package com.codeit.sb13.playlist_for_everyone.domain.auth.repository.impl;

import com.codeit.sb13.playlist_for_everyone.domain.auth.repository.RefreshTokenRepository;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * RefreshTokenRepository의 Redis 구현체.
 *
 * Key: auth:refresh<토큰의 SHA-256 해시>
 * Value: 사용자 UUID 문자열
 * TTL: 토큰의 남은 유효시간
 */

@Repository
public class RedisRefreshTokenRepository implements RefreshTokenRepository {

    private static final String KEY_PREFIX = "auth:refresh:";

    private final StringRedisTemplate redisTemplate;


    public RedisRefreshTokenRepository(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public void save(UUID userId, String refreshToken, Duration ttl) {
        Objects.requireNonNull(userId, "사용자 ID는 필수입니다.");
        Objects.requireNonNull(ttl, "토큰의 남은 유효시간은 필수 입니다.");

        // 이미 만료된 토큰을 저장하지 않는다.
        if (ttl.isZero() || ttl.isNegative()) {
            throw new IllegalArgumentException("토큰의 남은 유효시간은 0보다 커야 합니다.");
        }

        String key = redisKey(refreshToken);

        // 값 저장과 TTL 설정을 하나의 Redis 명령으로 수행한다.
        // 저장 후 별도로 expire를 호출하는 사이에 장애가 발생하면
        // 만료시간이 없는 데이터가 남을 수 있으므로 함께 설정한다.
        redisTemplate.opsForValue()
                .set(key, userId.toString(), ttl);

    }

    @Override
    public Optional<UUID> findUserId(String refreshToken) {
        String key = redisKey(refreshToken);

        // 등록되지 않았거나 TTL이 지난 키는 null로 조회된다.
        String storedUserId = redisTemplate.opsForValue().get(key);

        return Optional.ofNullable(storedUserId)
                .map(UUID::fromString);
    }

    @Override
    public void delete(String refreshToken) {
        String key = redisKey(refreshToken);

        // Redis DEL은 키가 이미 없어도 오류를 발생시키지 않는다.
        redisTemplate.delete(key);
    }

    /**
     * 토큰 원문을 저장하지 않고 해시값으로 Redis 키를 만든다.
     *
     * 저장·조회·삭제에서 동일한 변환을 사용해야 같은 키를 찾을 수 있다.
     */
    private String redisKey(String refreshToken) {
        if (refreshToken == null || refreshToken.isBlank()) {
            throw new IllegalArgumentException("Refresh Token은 필수 입니다.");
        }

        try {
            // MessageDigest는 호출마다 생성해 동시 요청 간 상태 공유를 피한다.
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(refreshToken.getBytes(StandardCharsets.UTF_8));
            return KEY_PREFIX + HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256을 사용할 수 없습니다.", exception);
        }

    }


}
