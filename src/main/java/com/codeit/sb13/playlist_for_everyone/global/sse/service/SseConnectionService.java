package com.codeit.sb13.playlist_for_everyone.global.sse.service;

import com.codeit.sb13.playlist_for_everyone.global.sse.config.SseProperties;
import com.codeit.sb13.playlist_for_everyone.global.sse.event.Connection;
import com.codeit.sb13.playlist_for_everyone.global.sse.repository.SseEmitterRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

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

        sseEmitterRepository.save(userId, connection, emitter);
        return emitter;
    }
}
