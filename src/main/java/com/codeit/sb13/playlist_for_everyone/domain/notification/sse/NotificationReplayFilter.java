package com.codeit.sb13.playlist_for_everyone.domain.notification.sse;

import com.codeit.sb13.playlist_for_everyone.domain.notification.repository.NotificationRepository;
import com.codeit.sb13.playlist_for_everyone.global.sse.event.Event;
import com.codeit.sb13.playlist_for_everyone.global.sse.history.SseHistoryEntry;
import com.codeit.sb13.playlist_for_everyone.global.sse.replay.SseReplayFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Redis에는 남아 있지만 DB에서 읽음 처리로 삭제된 알림을 SSE 전송 대상에서 제외한다.
 * SSE 이벤트 UUID가 아닌 data.id의 DB 알림 ID를 수신자 조건과 함께 일괄 조회한다.
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

    @Override
    public List<SseHistoryEntry> filter(UUID receiverId, List<SseHistoryEntry> entries) {
        if (entries.isEmpty()) {
            return List.of();
        }

        List<UUID> notificationIds = entries.stream()
                .map(this::extractNotificationId)
                .toList();

        Set<UUID> existingIds = new HashSet<>(notificationRepository
                .findExistingIds(receiverId, notificationIds));

        return entries.stream()
                .filter(entry -> existingIds.contains(extractNotificationId(entry)))
                .toList();
    }

    private UUID extractNotificationId(SseHistoryEntry entry){
        return UUID.fromString(entry.event().data().get("id").asString());
    }
}
