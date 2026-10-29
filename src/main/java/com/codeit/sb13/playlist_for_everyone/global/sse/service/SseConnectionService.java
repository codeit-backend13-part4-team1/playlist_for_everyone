package com.codeit.sb13.playlist_for_everyone.global.sse.service;

import com.codeit.sb13.playlist_for_everyone.global.sse.config.SseProperties;
import com.codeit.sb13.playlist_for_everyone.global.sse.event.Connection;
import com.codeit.sb13.playlist_for_everyone.global.sse.repository.SseEmitterRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class SseConnectionService {

    private final SseEmitterRepository sseEmitterRepository;
    private final SseProperties sseProperties;

    public SseEmitter connect(UUID userId){
        Connection connection = Connection.create();
        SseEmitter emitter = new SseEmitter(sseProperties.connectionTimeout().toMillis());
        emitter.onCompletion(()-> sseEmitterRepository.delete(userId, connection));
        emitter.onTimeout(()-> {
            sseEmitterRepository.delete(userId, connection);
            emitter.complete();
        });
        emitter.onError(e -> {
            sseEmitterRepository.delete(userId, connection);
            log.error("SSE 연결 오류로 연결을 정리했습니다. userId={}, connectionId={}", userId, connection.id(), e);
        });

        sseEmitterRepository.save(userId, connection, emitter);

        try {
            emitter.send(SseEmitter.event().comment("connected"));
        } catch (IOException | IllegalStateException e) {
            handleConnectionError(userId, e, connection);
        }
        return emitter;
    }

    public void closeAllLocalConnections(){
        sseEmitterRepository.findAll()
                .forEach((userId , connections )->{
                    connections.forEach((connection, emitter)->{
                        try {
                            emitter.complete();
                        }catch (RuntimeException e){
                            log.warn("SSE 연결 종료에 실패했습니다. userId={}, connectionId={}", userId, connection.id(),e);
                        }finally {
                            sseEmitterRepository.delete(userId, connection);
                        }
                    });
                });
    }

    private void handleConnectionError(UUID userId, Exception e, Connection connection) {
        sseEmitterRepository.delete(userId, connection);
        if (e instanceof IOException io){
            throw new UncheckedIOException("SSE 초기 주석 전송에 실패했습니다.", io);
        }
        if (e instanceof IllegalStateException illegalState){
            throw illegalState;
        }
    }
}
