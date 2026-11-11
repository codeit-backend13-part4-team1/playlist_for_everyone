package com.codeit.sb13.playlist_for_everyone.domain.notification.dto.event;

import com.codeit.sb13.playlist_for_everyone.domain.notification.entity.NotificationLevel;

import java.util.UUID;

/**
 * 업무 처리 결과를 알림으로 저장해 달라는 공통 요청이다. 아직 저장된 알림의 ID는 없다.
 *
 * <p>NotificationEventListener는 트랜잭션 안에서 발행된 요청을 커밋 후 처리하고,
 * 트랜잭션 밖에서 발행된 요청은 fallbackExecution=true에 따라 즉시 처리한다.
 * 업무 이벤트 연동에서는 NotificationDomainEventListener가 업무 커밋을 확인한 뒤
 * 수신자와 문구를 결정하여 이 요청을 재발행한다.
 *
 * <p>요청 발행은 저장 결과를 반환하지 않는다. 실제 저장은 NotificationService의
 * 독립 트랜잭션에서 수행하며, 저장 후 SSE 발행에는 별도의 NotificationCreatedEvent를 사용한다.
 */
public record NotificationCreateRequestedEvent(UUID receiverId, String title, String content, NotificationLevel level) {
}
