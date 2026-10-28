package com.codeit.sb13.playlist_for_everyone.global.sse.repository.impl;

import com.codeit.sb13.playlist_for_everyone.global.sse.event.Connection;
import com.codeit.sb13.playlist_for_everyone.global.sse.repository.SseEmitterRepository;
import org.springframework.stereotype.Repository;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Repository
public class InMemorySseEmitterRepository implements SseEmitterRepository {

    private final Map<UUID, Map<Connection, SseEmitter>> sseEmitters = new ConcurrentHashMap<>();

    @Override
    public void save(UUID userId, Connection connection, SseEmitter sseEmitter) {
      sseEmitters.compute(userId, (id, connections)->{
          Map<Connection, SseEmitter> userConnections = initializeConnections(connections);
          userConnections.put(connection, sseEmitter);
          return userConnections;
      });
    }

    @Override
    public Map<Connection, SseEmitter> findAllByUserId(UUID userId) {
        return Map.copyOf(sseEmitters.getOrDefault(userId, Map.of()));
    }

    @Override
    public void delete(UUID userId, Connection connection) {
        sseEmitters.compute(userId, (id, connections)->{
            if (connections == null){
                return null;
            }
            connections.remove(connection);
            return removeEmptyConnections(connections);
        });
    }

    @Override
    public Map<UUID, Map<Connection, SseEmitter>> findAll() {
        Map<UUID, Map<Connection, SseEmitter>> result = new HashMap<>();
        sseEmitters.keySet()
                .forEach(userId -> sseEmitters.computeIfPresent(userId,
                        (id, connections) -> {
                    result.put(userId, Map.copyOf(connections));
                    return connections;
                }));

        return Map.copyOf(result);
    }

    private Map<Connection, SseEmitter> removeEmptyConnections(Map<Connection, SseEmitter> connections) {
        if (connections == null || connections.isEmpty()){
            return null;
        }
        return connections;
    }

    private Map<Connection, SseEmitter> initializeConnections(Map<Connection, SseEmitter> connections) {
        if (connections == null){
            return new ConcurrentHashMap<>();
        }
        return connections;
    }

}
