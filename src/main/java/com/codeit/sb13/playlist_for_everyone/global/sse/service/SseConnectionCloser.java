package com.codeit.sb13.playlist_for_everyone.global.sse.service;

import com.codeit.sb13.playlist_for_everyone.global.sse.event.Connection;
import com.codeit.sb13.playlist_for_everyone.global.sse.repository.SseEmitterRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.UUID;

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
            sseEmitterRepository.delete(userId, connection);
        }
    }
}
