package com.codeit.sb13.playlist_for_everyone.domain.notification.repository;

import com.codeit.sb13.playlist_for_everyone.domain.notification.dto.repository.NotificationSearchCondition;
import com.codeit.sb13.playlist_for_everyone.domain.notification.entity.Notification;

import java.util.List;
import java.util.UUID;

public interface NotificationCustomRepository {

    /** 수신자의 알림을 생성시각·ID 순서로 최대 limit + 1건 조회한다. 추가 한 건은 다음 페이지 판단용이다. */
    List<Notification> search(NotificationSearchCondition condition);

    /** 커서 위치를 제외하고, 같은 수신자의 전체 알림 수를 조회한다. */
    long searchCount(NotificationSearchCondition condition);

    /**
     * SSE 필터가 전달한 DB 알림 ID 중 해당 수신자에게 현재 남은 ID를 일괄 조회한다.
     * 반환 순서가 이력 전송 순서를 정하지는 않으며, 필터가 원본 목록의 순서를 유지한다.
     */
    List<UUID> findExistingIds(UUID receiverId, List<UUID> notificationIds);
}
