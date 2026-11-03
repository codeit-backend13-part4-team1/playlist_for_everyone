package com.codeit.sb13.playlist_for_everyone.domain.notification.dto.event;

import com.codeit.sb13.playlist_for_everyone.domain.notification.entity.NotificationLevel;

import java.util.UUID;

/**
 * 업무 처리 결과를 알림으로 저장해 달라는 공통 요청이다. 아직 저장된 알림의 ID는 없다.
 *
 * <p>호출자는 업무 트랜잭션 안에서 발행한다. NotificationEventListener가 해당 업무의
 * 커밋 후 요청을 받아 별도 트랜잭션으로 저장하므로, 업무가 롤백되면 알림도 생성하지 않는다.
 * 트랜잭션 밖에서 발행한 요청은 현재 리스너가 처리하지 않는다.
 * 저장 후 SSE 발행에 사용하는 NotificationCreatedEvent와 구분한다.
 */
public record NotificationCreateRequestedEvent(UUID receiverId, String title, String content, NotificationLevel level) {
}
