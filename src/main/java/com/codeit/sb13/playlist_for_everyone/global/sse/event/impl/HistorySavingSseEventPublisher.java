package com.codeit.sb13.playlist_for_everyone.global.sse.event.impl;

import com.codeit.sb13.playlist_for_everyone.global.sse.event.SseEvent;
import com.codeit.sb13.playlist_for_everyone.global.sse.event.SseEventPublisher;
import com.codeit.sb13.playlist_for_everyone.global.sse.history.SseHistoryStore;
import lombok.RequiredArgsConstructor;
import tools.jackson.databind.ObjectMapper;

@RequiredArgsConstructor
public class HistorySavingSseEventPublisher implements SseEventPublisher {

    private final SseHistoryStore historyStore;
    private final SseEventPublisher delegate;
    private final ObjectMapper objectMapper;

    @Override
    public void publish(SseEvent<?> sseEvent) {

    }
}
