package com.codeit.sb13.playlist_for_everyone.domain.content.service.elasticsearch;

import lombok.RequiredArgsConstructor;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * 콘텐츠 색인 작업의 동시성 제어를 담당한다.
 *
 * <p>다중 서버 환경에서 이전 DB 스냅샷이 최신 색인 문서를
 * 덮어쓰거나 삭제된 문서를 다시 생성하는 문제를 방지하기 위해
 * 모든 콘텐츠 색인 작업에 공통 분산 락을 적용한다.
 */
@Component
@RequiredArgsConstructor
public class ContentIndexLock {
    // 초기 색인, 일괄 색인, 단건 색인 및 삭제 작업이 공유하는 락 키
    private static final String LOCK_KEY = "everyone:lock:content:index";
    // 분산 락 획득 최대 대기 시간(초)
    private static final long WAIT_TIME = 30L;

    private final RedissonClient redissonClient;

    /**
     * 분산 락을 획득한 후 반환값이 있는 작업을 실행한다.
     * DB 조회부터 Elasticsearch 저장·삭제까지 락을 유지한다.
     *
     * @throws IllegalArgumentException 락 획득 시간 초과 또는 인터럽트 발생 시
     */
    public <T> T execute(Supplier<T> action) {
        RLock lock = redissonClient.getLock(LOCK_KEY);

        try {
            if (!lock.tryLock(WAIT_TIME, TimeUnit.SECONDS)) {
                throw new IllegalStateException("콘텐츠 색인 락 획득 시간 초과");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("콘텐츠 색인 락 획득 중 인터럽트 발생", e);
        }

        try {
            return action.get();
        } finally {
            lock.unlock();
        }
    }

    /**
     * 분산 락을 획득한 후 반환값이 없는 작업을 실행한다.
     */
    public void execute(Runnable action) {
        execute(() -> {
            action.run();
            return null;
        });
    }

}
