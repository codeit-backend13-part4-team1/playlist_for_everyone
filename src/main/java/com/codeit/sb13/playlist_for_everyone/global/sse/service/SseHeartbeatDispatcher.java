package com.codeit.sb13.playlist_for_everyone.global.sse.service;

import com.codeit.sb13.playlist_for_everyone.global.sse.event.Connection;
import com.codeit.sb13.playlist_for_everyone.global.sse.event.ConnectionKey;
import com.codeit.sb13.playlist_for_everyone.global.sse.repository.SseEmitterRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.*;
import java.util.concurrent.RejectedExecutionException;

@RequiredArgsConstructor
@Slf4j
public class SseHeartbeatDispatcher {

    private final SseHeartbeatWorker heartbeatWorker;
    private final SseEmitterRepository repository;
    private final Object workLock = new Object();
    private final Map<ConnectionKey, ConnectionWork> submittedConnections = new HashMap<>();
    private boolean shuttingDown;
    private final ThreadPoolTaskExecutor heartbeatExecutor;

    public void submit(UUID userId, Connection connection) {
        enqueue(new ConnectionKey(userId, connection), false);
    }

    public void requestClose(UUID userId, Connection connection) {
        enqueue(new ConnectionKey(userId, connection), true);
    }

    /**
     * 신규 작업과 보류된 종료 요청의 재제출을 차단하고, 시작하지 않은 작업을 폐기한다.
     * 실행기에 종료를 요청해 실행 중인 워커에 인터럽트를 전달한다.
     *
     * @return 종료를 판정한 시점에 워커가 사용 중이던 연결 키의 스냅샷
     */
    public Set<ConnectionKey> shutdown(){
        Set<ConnectionKey> runningConnections;

        synchronized (workLock){
            shuttingDown = true;
            submittedConnections.entrySet()
                    .removeIf(entry -> !entry.getValue().running);

            runningConnections =  Set.copyOf(submittedConnections.keySet());
        }
        heartbeatExecutor.shutdown();
        return runningConnections;
    }

    public void retryPendingClosures() {
        List<Map.Entry<ConnectionKey, ConnectionWork>> pending;
        synchronized (workLock) {
            if (shuttingDown) {
                return;
            }
            pending = submittedConnections.entrySet().stream()
                    .filter(entry -> !entry.getValue().submitted)
                    .map(entry -> Map.entry(entry.getKey(), entry.getValue()))
                    .toList();
        }

        pending.forEach(entry -> {
            ConnectionKey key = entry.getKey();
            ConnectionWork work = entry.getValue();

            boolean registered = repository.findByUserIdAndConnection(key.userId(), key.connection())
                    .isPresent();

            synchronized (workLock) {
                if (shuttingDown || submittedConnections.get(key) != work || work.submitted) {
                    return;
                }
                if (!registered) {
                    submittedConnections.remove(key);
                    return;
                }
                work.submitted = true;
            }
            submitWork(key, work);
        });
    }

    private void enqueue(ConnectionKey key, boolean closeRequested) {
        ConnectionWork work;
        synchronized (workLock) {
            if (shuttingDown){
                return;
            }

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

        submitWork(key, work);
    }

    private void submitWork(ConnectionKey key, ConnectionWork work) {
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
                if (submittedConnections.get(key) != work) {
                    return;
                }

                if (work.closeRequested) {
                    work.submitted = false;
                    return;
                }

                submittedConnections.remove(key);
            }
        }
    }

    private void runWork(ConnectionKey key, ConnectionWork work) {
        boolean closeFirst;
        synchronized (workLock) {
            if (shuttingDown || submittedConnections.get(key) != work) {
                return;
            }
            work.running = true;
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
            if (!shuttingDown && work.closeRequested) {
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

    private static final class ConnectionWork {
        private boolean submitted = true;
        private boolean closeRequested;
        private boolean running;

        private ConnectionWork(boolean closeRequested) {
            this.closeRequested = closeRequested;
        }
    }
}
