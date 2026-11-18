package com.codeit.sb13.playlist_for_everyone.domain.notification.event;

import com.codeit.sb13.playlist_for_everyone.domain.notification.dto.response.NotificationDto;

import java.util.UUID;

/**
 * 저장한 알림의 스냅샷을 전송 단계에 넘기는 이벤트다.
 *
 * <p>NotificationService가 알림 저장 트랜잭션 안에서 발행한다. 이벤트가 발행되었다는 사실만으로
 * 커밋이 완료된 것은 아니며, NotificationSseEventListener는 커밋이 성공한 뒤에만 실행된다.
 * JPA 엔티티를 담지 않아 직렬화에 지연 로딩이 필요하지 않다. 실제 전송 시 알림 존재 여부는 별도 필터가 조회한다.
 *
 * @param eventId 생성 시 한 번 부여하는 SSE 이벤트 UUID. 변환·전달 단계에서 유지하며,
 *                공유 이력에서 복구·재전송할 때도 재사용한다
 * @param notificationDto 저장된 알림의 ID·생성시각·수신자·제목·내용·수준을 담은 스냅샷
 */
public record NotificationCreatedEvent(
        UUID eventId,
        NotificationDto notificationDto
) {
}
