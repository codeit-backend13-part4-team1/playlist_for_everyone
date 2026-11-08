package com.codeit.sb13.playlist_for_everyone.domain.notification.sse;

import com.codeit.sb13.playlist_for_everyone.domain.notification.repository.NotificationRepository;
import com.codeit.sb13.playlist_for_everyone.global.sse.event.Event;
import com.codeit.sb13.playlist_for_everyone.global.sse.history.SseHistoryEntry;
import com.codeit.sb13.playlist_for_everyone.global.sse.replay.SseReplayFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
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
        return entries;
    }
}
