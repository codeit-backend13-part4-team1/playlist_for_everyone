package com.codeit.sb13.playlist_for_everyone.global.sse.event;

public interface SseEventPublisher {
    void publish(SseEvent<?> sseEvent);
}
