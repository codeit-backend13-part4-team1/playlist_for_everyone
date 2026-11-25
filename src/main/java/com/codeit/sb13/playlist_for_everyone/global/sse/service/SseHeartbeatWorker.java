package com.codeit.sb13.playlist_for_everyone.global.sse.service;

import com.codeit.sb13.playlist_for_everyone.global.sse.event.Connection;
import com.codeit.sb13.playlist_for_everyone.global.sse.repository.SseEmitterRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import org.springframework.web.util.DisconnectedClientHelper;

import java.io.IOException;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@RequiredArgsConstructor
@Slf4j
public class SseHeartbeatWorker {

    private final SseEmitterRepository repository;
    private final SseDeliveryCoordinator deliveryCoordinator;

    @Async("sseHeartbeatTaskExecutor")
    public CompletableFuture<Void> send(UUID userId, Connection connection) {
        repository.findByUserIdAndConnection(userId, connection)
                .ifPresent(emitter -> sendHeartbeat(userId, connection, emitter));

        return CompletableFuture.completedFuture(null);
    }

    private void sendHeartbeat(UUID userId, Connection connection, SseEmitter emitter) {
        try {
            // 주석 프레임은 알림/DM 이벤트로 전달되지 않으며 마지막 수신 이벤트 ID도 변경하지 않는다.
            emitter.send(SseEmitter.event().comment("heartbeat"));
        } catch (IOException | IllegalStateException e) {
            // 하트비트 send 실패 경로에서는 내부 참조를 정리한다. 이곳에서 complete를 다시 요청하지 않는다.
            deliveryCoordinator.cleanupConnection(userId, connection);
            logSseHeartbeatIssue(userId, connection, e);
        }
    }

    /**
     * 하트비트 전송 중 Spring이 식별한 클라이언트 단절은 DEBUG로만 남긴다.
     * 모든 I/O 예외가 단절은 아니므로, 나머지 실패는 원인을 확인할 수 있게 WARN에 스택을 남긴다.
     */
    private void logSseHeartbeatIssue(UUID userId, Connection connection, Exception e) {
        if (DisconnectedClientHelper.isClientDisconnectedException(e)){
            log.debug("SSE 하트비트 전송 중 클라이언트 단절로 연결을 정리했습니다. userId={}, connectionId={}", userId, connection.id());
            return;
        }
        log.warn("SSE 하트비트 전송 실패로 연결을 정리했습니다. userId={}, connectionId={}", userId, connection.id(), e);
    }
}
