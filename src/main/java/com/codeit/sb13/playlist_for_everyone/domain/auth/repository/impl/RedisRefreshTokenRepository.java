package com.codeit.sb13.playlist_for_everyone.domain.auth.repository.impl;

import com.codeit.sb13.playlist_for_everyone.domain.auth.repository.RefreshTokenRepository;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Repository;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.*;

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


    /**
     * 기존 Refresh Token을 새 토큰으로 교체한다.
     *
     * KEYS[1]: 기존 토큰의 Redis 키
     * KEYS[2]: 새 토큰의 Redis 키
     * ARGV[1]: 사용자 ID
     * ARGV[2]: 새 토큰의 TTL(밀리초)
     *
     * 반환값: 성공 1, 교체 조건 불충족 0
     */
    private static final DefaultRedisScript<Long> ROTATE_SCRIPT =
            new DefaultRedisScript<>("""
                -- 기존 토큰이 없거나 소유자가 다르면 변경하지 않는다.
                local storedUserId = redis.call('GET', KEYS[1])
                if storedUserId ~= ARGV[1] then
                    return 0
                end
                -- 새 키가 이미 있으면 덮어쓰지 않는다.
                -- PX: 밀리초 단위 TTL, NX: 키가 없을 때만 저장
                local saved = redis.call(
                    'SET', KEYS[2], ARGV[1], 'PX', ARGV[2], 'NX')
                if not saved then
                    return 0
                end
                -- 새 토큰 저장에 성공한 경우에만 기존 토큰을 폐기한다.
                redis.call('DEL', KEYS[1])
                return 1
                """, Long.class);


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

    @Override
    public boolean rotate(UUID userId, String oldRefreshToken, String newRefreshToken, Duration ttl) {
        Objects.requireNonNull(userId, "사용자 ID는 필수 입니다.");
        Objects.requireNonNull(ttl, "토큰의 남은 유효시간은 필수 입니다.");

        if (ttl.isZero() || ttl.isNegative()) {
            throw new IllegalArgumentException("토큰의 남은 유효시간은 0보다 커야 합니다.");
        }

        // Redis의 PX 옵션에 전달할 밀리초 단위로 변환한다.
        long ttlMillis = ttl.toMillis();

        // 양수인 Duration이라도 1밀리초 미만이면 변환 결과가 0이다.
        if (ttlMillis <= 0) {
            throw new IllegalArgumentException("토큰의 남은 유효시간은 1밀리초 이상이어야 합니다.");
        }

        // 기존 저장·조회와 동일하게 토큰 원문 대신 SHA-256 해시 키를 사용한다.
        String oldKey = redisKey(oldRefreshToken);
        String newKey = redisKey(newRefreshToken);

        if (oldKey.equals(newKey)) {
            throw new IllegalArgumentException("새 Refresh Token은 기존 토큰과 달라야 합니다.");
        }

        // 소유자 확인과 토큰 교체를 Redis 서버 안에서 한 번에 처리한다.
        Long result = redisTemplate.execute(
                ROTATE_SCRIPT,
                List.of(oldKey, newKey),
                userId.toString(),
                Long.toString(ttlMillis)
        );

        return Long.valueOf(1L).equals(result);
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
