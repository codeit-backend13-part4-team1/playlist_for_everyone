package com.codeit.sb13.playlist_for_everyone.domain.notification.sse;

import com.codeit.sb13.playlist_for_everyone.domain.notification.repository.NotificationRepository;
import com.codeit.sb13.playlist_for_everyone.global.sse.event.Event;
import com.codeit.sb13.playlist_for_everyone.global.sse.history.SseHistoryEntry;
import com.codeit.sb13.playlist_for_everyone.global.sse.replay.SseReplayFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

import java.util.*;

/**
 * Redis에는 남아 있지만 DB에서 읽음 처리로 삭제된 알림을 SSE 전송 대상에서 제외한다.
 * SSE 이벤트 UUID가 아닌 data.id의 DB 알림 ID를 수신자 조건과 함께 일괄 조회한다.
 * id가 없거나 UUID로 읽을 수 없는 항목은 제외한다. 한 항목 때문에 나머지 정상 알림의 복구를 막지 않는다.
 * 존재 여부를 조회한 시점의 필터이며, 이후 DB 삭제와 네트워크 전송을 원자적으로 묶지는 않는다.
 */
@Component
@RequiredArgsConstructor
public class NotificationReplayFilter implements SseReplayFilter {
    private final NotificationRepository notificationRepository;

    @Override
    public boolean supports(String eventName) {
        return Event.NOTIFICATIONS.getName().equals(eventName);
    }

    /**
     * 유효한 DB ID만 한 번에 조회하고 원본 순서로 남긴다. 유효한 ID가 없으면 DB를 호출하지 않는다.
     * DB 조회 실패는 그대로 전달한다. 제외 항목을 Redis에서 삭제하거나 커서를 진행하는 책임은 갖지 않는다.
     */
    @Override
    public List<SseHistoryEntry> filter(UUID receiverId, List<SseHistoryEntry> entries) {
        if (entries.isEmpty()) {
            return List.of();
        }

        List<UUID> notificationIds = entries.stream()
                .map(this::extractNotificationId)
                .filter(Objects::nonNull)
                .toList();

        if (notificationIds.isEmpty()) {
            return List.of();
        }

        Set<UUID> existingIds = new HashSet<>(notificationRepository
                .findExistingIds(receiverId, notificationIds));

        return entries.stream()
                .filter(entry -> existingIds.contains(extractNotificationId(entry)))
                .toList();
    }

    /** null은 DB 조회 대상에서 제외할 데이터라는 뜻이다. 잘못된 payload를 이력 조회 장애로 전파하지 않는다. */
    private UUID extractNotificationId(SseHistoryEntry entry){
        JsonNode data = entry.event().data();
        if (data == null){
            return null;
        }
        JsonNode id = data.path("id");
        if (!id.isString()){
            return null;
        }
        try {
            return UUID.fromString(id.asString());
        } catch (IllegalArgumentException e){
            return null;
        }
    }
}
