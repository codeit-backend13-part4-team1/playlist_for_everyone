package com.codeit.sb13.playlist_for_everyone.global.sse.service;

import com.codeit.sb13.playlist_for_everyone.global.sse.event.Connection;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.RejectedExecutionException;

@RequiredArgsConstructor
@Slf4j
public class SseHeartbeatDispatcher {

    private final SseHeartbeatWorker heartbeatWorker;
    private final Set<ConnectionKey> submittedConnections = ConcurrentHashMap.newKeySet();

    public void submit(UUID userId, Connection connection) {
        ConnectionKey key = new ConnectionKey(userId, connection);

        if (!submittedConnections.add(key)) {
            return;
        }

        try {
            heartbeatWorker.send(userId, connection)
                    .whenComplete((unused, failure) -> {
                        submittedConnections.remove(key);
                        if (failure != null) {
                            log.warn("SSE 하트비트 작업 실패. userId={}, connectionId={}",
                                    userId, connection.id(), failure);
                        }
                    });
        } catch (RejectedExecutionException e) {
            submittedConnections.remove(key);
        }
    }
    private record ConnectionKey(UUID userId, Connection connection) {
    }
}
