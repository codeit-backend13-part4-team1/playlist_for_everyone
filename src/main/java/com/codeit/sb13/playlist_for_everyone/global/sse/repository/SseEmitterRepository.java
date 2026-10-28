package com.codeit.sb13.playlist_for_everyone.global.sse.repository;

import com.codeit.sb13.playlist_for_everyone.global.sse.event.Connection;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Map;
import java.util.UUID;

public interface SseEmitterRepository {
    void save(UUID userId, Connection connection, SseEmitter sseEmitter);

    Map<Connection, SseEmitter> findAllByUserId(UUID userId);
}
