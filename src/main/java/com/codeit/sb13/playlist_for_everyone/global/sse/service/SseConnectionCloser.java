package com.codeit.sb13.playlist_for_everyone.global.sse.service;

import com.codeit.sb13.playlist_for_everyone.global.sse.event.Connection;
import com.codeit.sb13.playlist_for_everyone.global.sse.repository.SseEmitterRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.UUID;

/**
 * 재시도 소진·예약 작업의 전송 실패 시 HTTP 종료 요청과 emitter 저장소 정리를 담당한다.
 * 호출자는 먼저 coordinator 상태와 예약을 정리한다. complete가 실패하거나 완료 콜백이 늦어도
 * finally에서 로컬 연결 참조를 제거하며, Redis 공유 이력에는 손대지 않는다.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class SseConnectionCloser {

    private final SseEmitterRepository sseEmitterRepository;

    public void close(UUID userId, Connection connection, SseEmitter emitter){
        try {
            emitter.complete();
        } catch (RuntimeException e){
            log.warn(
                    "SSE 연결 종료 요청에 실패했습니다. userId={}, connectionId={}",
                    userId,
                    connection.id(),
                    e
            );
        } finally {
            cleanUp(userId, connection);
        }
    }

    public void cleanUp(UUID userId, Connection connection){
        sseEmitterRepository.delete(userId, connection);
    }
}
