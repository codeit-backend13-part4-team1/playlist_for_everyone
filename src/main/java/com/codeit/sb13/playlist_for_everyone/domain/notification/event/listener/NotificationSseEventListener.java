package com.codeit.sb13.playlist_for_everyone.domain.notification.event.listener;

import com.codeit.sb13.playlist_for_everyone.domain.notification.event.NotificationCreatedEvent;
import com.codeit.sb13.playlist_for_everyone.global.exception.sse.SsePublishException;
import com.codeit.sb13.playlist_for_everyone.global.sse.event.SseEvent;
import com.codeit.sb13.playlist_for_everyone.global.sse.event.SseEventPublisher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 알림 저장 커밋 후 DTO 스냅샷을 공통 SSE 발행 계약으로 전달한다.
 *
 * <p>브로커 구현체와 로컬 연결 저장소에 직접 의존하지 않는다. 수신자가 다른 서버에 연결될 수
 * 있으므로 현재 서버의 연결 유무를 검사하지 않고 발행한다. 각 서버의 공통 전송 기능이
 * 자기 서버에 있는 수신자의 연결을 찾아 전달한다.
 * 이 리스너는 DTO를 전달하며, 공통 발행기가 공유 이력을 저장한다. 실제 수신 경로의 알림 존재 여부 조회는
 * NotificationReplayFilter의 책임이다. 알림을 다시 생성하는 경로는 아니다.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class NotificationSseEventListener {

    private final SseEventPublisher sseEventPublisher;

    /**
     * 기본 AFTER_COMMIT, fallbackExecution=false로 알림 트랜잭션의 성공에만 반응한다.
     * 롤백되거나 트랜잭션 없이 발행된 이벤트는 처리하지 않는다. @Async를 사용하지 않으므로
     * 이력 저장과 Redis 발행 요청까지 커밋 처리 스레드에서 실행되며, 브라우저 수신 완료를 기다리지는 않는다.
     */
    @TransactionalEventListener
    public void handleNotificationCreated(NotificationCreatedEvent event){
        publishNotification(event);
    }

    /**
     * 직렬화·이력 저장·브로커 발행 실패로 전달된 SsePublishException만 이 단계에서 처리한다.
     * 저장된 알림은 유지하고 다음 알림을 처리할 수 있게 하며, 자동 재시도는 수행하지 않는다.
     * 필수 값 누락 등 이벤트 구성 오류는 이 예외 처리 범위에 포함하지 않는다.
     */
    private void publishNotification(NotificationCreatedEvent event) {
        try {
            sseEventPublisher.publish(SseEvent.createNotificationEvent(
                    event.eventId(),
                    event.notificationDto().receiverId(),
                    event.notificationDto()
            ));
        } catch (SsePublishException e){
            // 추적용 식별자와 원인 타입만 남긴다. 제목·내용·예외 메시지·스택은 출력하지 않는다.
            Throwable cause = e.getCause() != null ? e.getCause() : e;

            log.warn("알림 SSE 발행 실패. notificationId={}, eventId={}, receiverId={}, exceptionType={}",
                    event.notificationDto().id(),
                    event.eventId(),
                    event.notificationDto().receiverId(),
                    cause.getClass().getSimpleName());
        }
    }
}
