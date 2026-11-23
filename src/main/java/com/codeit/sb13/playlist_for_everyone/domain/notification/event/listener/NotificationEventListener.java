package com.codeit.sb13.playlist_for_everyone.domain.notification.event.listener;

import com.codeit.sb13.playlist_for_everyone.domain.notification.dto.command.NotificationCommand;
import com.codeit.sb13.playlist_for_everyone.domain.notification.dto.event.NotificationCreateRequestedEvent;
import com.codeit.sb13.playlist_for_everyone.domain.notification.monitoring.NotificationMetrics;
import com.codeit.sb13.playlist_for_everyone.domain.notification.service.NotificationService;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 공통 알림 생성 요청을 받아 독립된 트랜잭션으로 알림을 저장하는 리스너.
 *
 * <p>업무 이벤트에서 구성한 요청은 비동기 프로세서가 작업 스레드에서 재발행한다.
 * 이 요청은 트랜잭션 밖에서 발행되므로 {@code fallbackExecution = true}에 따라 같은 스레드에서 처리한다.
 *
 * <p>{@link NotificationService#save(NotificationCommand)}를 호출해 요청마다 {@code REQUIRES_NEW}로 알림을 저장한다.
 * 저장 중 발행한 이벤트는 알림 커밋 후 {@link NotificationSseEventListener}가 받아 SSE 발행을 요청한다.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class NotificationEventListener {

    private final NotificationService service;
    private final NotificationMetrics metrics;

    /**
     * AFTER_COMMIT, fallbackExecution=true로 공통 생성 요청을 처리한다.
     *
     * <p>트랜잭션에 등록된 요청은 커밋 후 실행하며, 롤백되면 실행하지 않는다.
     * 트랜잭션 없이 발행된 요청은 발행 스레드에서 즉시 처리한다.
     * 업무 이벤트 경로에서는 이 발행 스레드가 notification-* 작업 스레드다.
     * 두 경로 모두 @Async를 사용하지 않는다.
     * 처리 시간은 리스너 진입부터 저장 실패 또는 커밋 후 SSE 발행 시도 종료까지 측정한다.
     */
    @TransactionalEventListener(fallbackExecution = true)
    public void handleNotification(NotificationCreateRequestedEvent event){
        Timer.Sample sample = metrics.startProcessing();
        try {
            saveNotification(event);
        } finally {
            metrics.recordProcessing(event.source(), sample);
        }
    }

    private void saveNotification(NotificationCreateRequestedEvent event) {
        NotificationCommand command = NotificationCommand.from(event);
        try {
            service.save(command);
        }catch (Exception e){
            // 저장 서비스 호출 실패를 기록하여 이미 커밋된 업무와 후속 알림 처리를 분리한다.
            // 예상한 SSE 발행 실패는 NotificationSseEventListener가 따로 처리한다.
            metrics.recordSaveFailure();
            log.warn("알림 저장 실패. receiverId={}", event.receiverId(), e);
        }
    }
}
