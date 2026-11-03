package com.codeit.sb13.playlist_for_everyone.domain.notification.event;

import com.codeit.sb13.playlist_for_everyone.domain.notification.dto.response.NotificationDto;

import java.util.UUID;

/**
 * 저장한 알림의 스냅샷을 전송 단계에 넘기는 이벤트다.
 *
 * <p>NotificationService가 알림 저장 트랜잭션 안에서 발행한다. 이벤트가 발행되었다는 사실만으로
 * 커밋이 완료된 것은 아니며, NotificationSseEventListener는 커밋이 성공한 뒤에만 실행된다.
 * JPA 엔티티를 담지 않아 전송 단계에서 영속성 컨텍스트나 추가 조회가 필요하지 않다.
 *
 * @param eventId 생성 시 한 번 부여하는 SSE 이벤트 UUID. 변환·전달 단계에서 유지하며,
 *                향후 동일 이벤트를 재전달할 때도 재사용한다. 현재 자동 재전송 기능은 없다
 * @param notificationDto 저장된 알림의 ID·생성시각·수신자·제목·내용·수준을 담은 스냅샷
 */
public record NotificationCreatedEvent(
        UUID eventId,
        NotificationDto notificationDto
) {
}
