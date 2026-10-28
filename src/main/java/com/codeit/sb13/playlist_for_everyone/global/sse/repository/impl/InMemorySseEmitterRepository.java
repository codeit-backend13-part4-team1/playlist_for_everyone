package com.codeit.sb13.playlist_for_everyone.global.sse.repository.impl;

import com.codeit.sb13.playlist_for_everyone.global.sse.event.Connection;
import com.codeit.sb13.playlist_for_everyone.global.sse.repository.SseEmitterRepository;
import org.springframework.stereotype.Repository;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Repository
public class InMemorySseEmitterRepository implements SseEmitterRepository {

    private final Map<UUID, Map<Connection, SseEmitter>> sseEmitters = new ConcurrentHashMap<>();

    @Override
    public void save(UUID userId, Connection connection, SseEmitter sseEmitter) {

    }

    @Override
    public Map<Connection, SseEmitter> findAllByUserId(UUID userId) {
        return null;
    }
}
