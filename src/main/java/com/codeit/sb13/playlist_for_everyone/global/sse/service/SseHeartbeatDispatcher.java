package com.codeit.sb13.playlist_for_everyone.global.sse.service;

import com.codeit.sb13.playlist_for_everyone.global.sse.event.Connection;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.RejectedExecutionException;

@RequiredArgsConstructor
@Slf4j
public class SseHeartbeatDispatcher {

    private final SseHeartbeatWorker heartbeatWorker;
    private final Object workLock = new Object();
    private final Map<ConnectionKey, ConnectionWork> submittedConnections = new HashMap<>();

    public void submit(UUID userId, Connection connection) {
        enqueue(new ConnectionKey(userId, connection), false);
    }

    public void requestClose(UUID userId, Connection connection) {
        enqueue(new ConnectionKey(userId, connection), true);
    }

    private void enqueue(ConnectionKey key, boolean closeRequested) {
        ConnectionWork work;
        synchronized (workLock) {
            ConnectionWork existing = submittedConnections.get(key);
            if (existing != null) {
                if (closeRequested) {
                    existing.closeRequested = true;
                }
                return;
            }
            work = new ConnectionWork(closeRequested);
            submittedConnections.put(key, work);
        }

        try {
            heartbeatWorker.runAsync(() -> runWork(key, work))
                    .whenComplete((unused, failure) -> {
                        if (failure != null) {
                            log.warn("SSE 하트비트 작업 실패. userId={}, connectionId={}",
                                    key.userId(), key.connection().id(), failure);
                        }
                    });
        } catch (RejectedExecutionException e) {
            synchronized (workLock) {
                submittedConnections.remove(key, work);
            }
        }
    }

    private void runWork(ConnectionKey key, ConnectionWork work) {
        boolean closeFirst;
        synchronized (workLock) {
            if (submittedConnections.get(key) != work) {
                return;
            }
            closeFirst = work.closeRequested;
        }
        if (closeFirst) {
            closeAndRemove(key, work);
            return;
        }

        try {
            heartbeatWorker.send(key.userId(), key.connection());
        } finally {
            if (finishSendAndCheckForClose(key, work)) {
                closeAndRemove(key, work);
            }
        }
    }

    private boolean finishSendAndCheckForClose(ConnectionKey key, ConnectionWork work) {
        synchronized (workLock) {
            if (work.closeRequested) {
                return true;
            }

            submittedConnections.remove(key, work);
            return false;
        }
    }

    private void closeAndRemove(ConnectionKey key, ConnectionWork work) {
        try {
            heartbeatWorker.close(key.userId(), key.connection());
        } finally {
            synchronized (workLock) {
                submittedConnections.remove(key, work);
            }
        }
    }

    private record ConnectionKey(UUID userId, Connection connection) {
    }

    private static final class ConnectionWork {
        private boolean closeRequested;

        private ConnectionWork(boolean closeRequested) {
            this.closeRequested = closeRequested;
        }
    }
}
