package com.codeit.sb13.playlist_for_everyone.global.sse.replay;

import com.codeit.sb13.playlist_for_everyone.global.sse.history.SseHistoryEntry;

import java.util.List;
import java.util.UUID;

public interface SseReplayFilter {
    boolean supports(String eventName);

    List<SseHistoryEntry> filter(UUID receiverId, List<SseHistoryEntry> entries);
}
