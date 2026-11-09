package com.codeit.sb13.playlist_for_everyone.domain.notification.event.listener;

import com.codeit.sb13.playlist_for_everyone.domain.notification.dto.command.NotificationCommand;
import com.codeit.sb13.playlist_for_everyone.domain.notification.dto.event.NotificationCreateRequestedEvent;
import com.codeit.sb13.playlist_for_everyone.domain.notification.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 공통 알림 생성 요청을 받아 독립된 트랜잭션으로 알림을 저장하는 리스너.
 *
 * <p>업무 이벤트의 커밋 여부는 NotificationDomainEventListener가 확인한다.
 * 이 리스너는 업무 커밋 후 별도 변환 트랜잭션 없이 재발행된 요청도 처리하도록
 * fallbackExecution=true를 사용한다.
 *
 * <p>별도 Spring 빈인 NotificationService.save를 호출하여 REQUIRES_NEW로 저장한다.
 * 저장 중 발행한 NotificationCreatedEvent는 알림 커밋 후
 * NotificationSseEventListener가 받아 SSE 발행을 요청한다.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class NotificationEventListener {

    private final NotificationService service;

    /**
     * AFTER_COMMIT, fallbackExecution=true로 공통 생성 요청을 처리한다.
     *
     * <p>트랜잭션에 등록된 요청은 커밋 후 실행하며, 롤백되면 실행하지 않는다.
     * 트랜잭션 없이 발행된 요청은 발행 스레드에서 즉시 처리한다.
     * 두 경로 모두 @Async를 사용하지 않는다.
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
