package com.codeit.sb13.playlist_for_everyone.domain.notification.event;

import com.codeit.sb13.playlist_for_everyone.domain.notification.config.NotificationExecutorLifecycle;
import com.codeit.sb13.playlist_for_everyone.domain.notification.dto.event.NotificationPublishBatch;
import com.codeit.sb13.playlist_for_everyone.domain.notification.monitoring.NotificationMetrics;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/**
 * 완성된 알림 요청 목록을 전용 실행기의 한 작업에서 순차 발행한다.
 * 수신자 조회·문구 구성은 업무 이벤트 리스너가 담당하며, 이 컴포넌트는 정책을 다시 판단하지 않는다.
 *
 * <p>목록 전체를 감싸는 트랜잭션은 열지 않는다. 공통 요청 리스너가 수신자별 REQUIRES_NEW로 저장하고,
 * 알림 커밋 후 SSE 발행도 같은 작업 스레드에서 이어진다. 개별 발행 실패는 다음 요청을 막지 않는다.
 * 제출 거부는 이 메서드에 진입하기 전 프록시에서 발생하므로 호출한 리스너가 처리한다.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class NotificationAsyncProcessor {

    private final NotificationMetrics metrics;
    private final ApplicationEventPublisher eventPublisher;
    private final NotificationExecutorLifecycle executorLifecycle;

    @Async("notificationTaskExecutor")
    public void publish(NotificationPublishBatch batch) {
        for (var request : batch.requests()) {
            // 정상 종료 대기 중에는 계속 처리하고, 중단 요청 이후의 수신자만 발행하지 않는다.
            if (Thread.currentThread().isInterrupted() || executorLifecycle.isStopRequested()) {
                return;
            }
            metrics.recordPublishAttempt(request.source());
            try {
                eventPublisher.publishEvent(request);
            } catch (RuntimeException failure) {
                metrics.recordPublishFailure();
                log.warn("{} 알림 재발행 실패. sourceIds={}, receiverId={}, exceptionType={}",
                        batch.source(), batch.sourceIds(), request.receiverId(), failure.getClass().getSimpleName());
            }
        }
    }
}
