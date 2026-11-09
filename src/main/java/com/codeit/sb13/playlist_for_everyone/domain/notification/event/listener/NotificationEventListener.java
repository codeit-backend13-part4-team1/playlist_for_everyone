package com.codeit.sb13.playlist_for_everyone.domain.notification.event.listener;

import com.codeit.sb13.playlist_for_everyone.domain.notification.dto.command.NotificationCommand;
import com.codeit.sb13.playlist_for_everyone.domain.notification.dto.event.NotificationCreateRequestedEvent;
import com.codeit.sb13.playlist_for_everyone.domain.notification.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 업무 트랜잭션의 커밋 이후 공통 알림 생성 요청을 저장 서비스에 연결한다.
 *
 * <p>별도 Spring 빈인 NotificationService를 호출하므로 save의 REQUIRES_NEW가 적용된다.
 * 이미 끝난 업무 트랜잭션에 추가 저장하지 않고, 알림만의 새 트랜잭션으로 저장을 확정한다.
 * SSE 전송은 저장 중 발행된 NotificationCreatedEvent를 다른 리스너가 받아 처리한다.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class NotificationEventListener {

    private final NotificationService service;

    /**
     * 기본 AFTER_COMMIT, fallbackExecution=false를 사용한다.
     * 업무 롤백·트랜잭션 외 발행 시에는 호출되지 않으며, @Async 없이 커밋 처리 스레드에서 실행된다.
     */
    @TransactionalEventListener(fallbackExecution = true)
    public void handleNotification(NotificationCreateRequestedEvent event){
        NotificationCommand command = NotificationCommand.from(event);
        try {
            service.save(command);
        }catch (Exception e){
            // 저장 서비스 호출 실패를 기록하여 이미 커밋된 업무와 후속 알림 처리를 분리한다.
            // 예상한 SSE 발행 실패는 NotificationSseEventListener가 따로 처리한다.
            log.warn("알림 저장 실패. receiverId={}", event.receiverId(), e);
        }
    }
}
