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
