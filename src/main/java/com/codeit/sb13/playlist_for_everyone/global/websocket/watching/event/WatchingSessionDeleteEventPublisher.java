package com.codeit.sb13.playlist_for_everyone.global.websocket.watching.event;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.QueryTimeoutException;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Recover;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
public class WatchingSessionDeleteEventPublisher {

    private final RedisTemplate<String, Object> redisTemplate;

    // Redis 연결 실패 또는 작업 타임아웃 발생 시 최대 3회 재시도
    @Retryable(
            retryFor = {
                    RedisConnectionFailureException.class,
                    QueryTimeoutException.class
            },
            maxAttempts = 3,    // 최초 실행 포함 최대 3회 시도
            backoff = @Backoff(
                    delay = 500,    // 첫 재시도 전 500ms 대기
                    multiplier = 2  // 재시도마다 대기 시간 2배 증가
            )
    )
    public void publish(String topic, UUID contentId) {
        redisTemplate.convertAndSend(
                topic,
                contentId.toString()
        );
    }

    // 재시도를 모두 실패했을 때 실행
    @Recover
    public void recover(RuntimeException e, String topic, UUID contentId) {
        log.error(
                "콘텐츠 삭제 이벤트 Redis 발행 최종 실패. topic={}, contentId={}",
                topic, contentId, e
        );

        throw e;
    }

}
