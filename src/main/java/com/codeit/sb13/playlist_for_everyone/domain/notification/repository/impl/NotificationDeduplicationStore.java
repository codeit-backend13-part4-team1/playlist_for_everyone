package com.codeit.sb13.playlist_for_everyone.domain.notification.repository.impl;

import com.codeit.sb13.playlist_for_everyone.domain.notification.config.NotificationDeduplicationProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class NotificationDeduplicationStore {

    private static final String KEY_PREFIX = "everyone:notification:deduplication:";

    private static final DefaultRedisScript<Long> RELEASE_IF_OWNED =new DefaultRedisScript<>("""
            if redis.call('GET', KEYS[1]) == ARGV[1]
                then return redis.call('DEL', KEYS[1])
            end

            return 0
            """, Long.class);

    private final StringRedisTemplate redisTemplate;

    private final NotificationDeduplicationProperties properties;

    public Optional<Reservation> tryReserve(String actionKey){
        String key = KEY_PREFIX + actionKey;
        String token = UUID.randomUUID().toString();

        Boolean reserved = redisTemplate.opsForValue().setIfAbsent(key, token, properties.window());

        if (!Boolean.TRUE.equals(reserved)){
            return Optional.empty();
        }

        return Optional.of(new Reservation(key, token));
    }

    public void release(Reservation reservation){
        redisTemplate.execute(
                RELEASE_IF_OWNED,
                List.of(reservation.key()),
                reservation.token()
        );
    }

    public record Reservation(String key, String token){}
}
