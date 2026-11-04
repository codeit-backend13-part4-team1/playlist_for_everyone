package com.codeit.sb13.playlist_for_everyone.domain.notification.dto.response;

import com.codeit.sb13.playlist_for_everyone.domain.notification.entity.Notification;
import com.codeit.sb13.playlist_for_everyone.domain.notification.entity.NotificationLevel;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 알림 조회 응답과 notifications SSE의 data에 공통으로 사용하는 불변 스냅샷이다.
 *
 * <p>저장 서비스가 엔티티의 값을 복사하므로 커밋 후 전송 리스너는 엔티티를 다시 조회하지 않는다.
 * id는 DB 알림 식별자이며, SSE 프레임의 id로 사용하는 별도 이벤트 식별자와 다르다.
 * 이벤트 이름과 이벤트 식별자는 이 DTO를 감싸는 내부 SseEvent가 담당한다.
 */
public record NotificationDto(
        UUID id,
        LocalDateTime createdAt,
        UUID receiverId,
        String title,
        String content,
        NotificationLevel level
) {

    /**
     * 엔티티에 이미 채워진 값을 복사한다. ID·생성시각을 새로 만들거나 DB에 flush하지 않는다.
     * 수신자는 getReceiverId로 UUID만 복사하여 User 엔티티를 응답이나 이벤트에 담지 않는다.
     * 현재 매핑에서 DTO 변환 중 추가 사용자 조회가 없는지는 NotificationRepositoryTest에서 검증한다.
     */
    public static NotificationDto from(Notification notification){
        return new NotificationDto(
                notification.getId(),
                notification.getCreatedAt(),
                notification.getReceiverId(),
                notification.getTitle(),
                notification.getContent(),
                notification.getLevel()
        );
    }
}
