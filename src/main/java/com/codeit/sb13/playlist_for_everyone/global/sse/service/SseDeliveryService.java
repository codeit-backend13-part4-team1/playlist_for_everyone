package com.codeit.sb13.playlist_for_everyone.global.sse.service;

import com.codeit.sb13.playlist_for_everyone.global.sse.event.Connection;
import com.codeit.sb13.playlist_for_everyone.global.sse.event.SseEvent;
import com.codeit.sb13.playlist_for_everyone.global.sse.repository.SseEmitterRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class SseDeliveryService {

    private final SseEmitterRepository sseEmitterRepository;

    public void sendToLocalConnections(SseEvent<?> sseEvent){
        Map<Connection, SseEmitter> connections = sseEmitterRepository.findAllByUserId(sseEvent.receiverId());

        connections.forEach((connection, emitter)->{
            try {
                emitter.send(
                        SseEmitter.event()
                        .id(sseEvent.eventId().toString())
                        .name(sseEvent.eventName())
                        .data(sseEvent.data()
                        )
                );
            } catch (IOException | IllegalStateException e) {
                sseEmitterRepository.delete(sseEvent.receiverId(), connection);
                log.warn("SSE 이벤트 전송 실패로 연결을 정리했습니다. eventId={}, receiverId={}, connectionId={}"
                        , sseEvent.eventId(), sseEvent.receiverId(), connection.id(), e);
            }
        });

    }

    public void sendHeartbeatToLocalConnections(){
        sseEmitterRepository.findAll()
                .forEach((userId, connections) ->
                        connections.forEach((connection, emitter) ->
                                sendHeartbeat(userId, connection, emitter)
                        )
                );
    }

    private void sendHeartbeat(UUID userId, Connection connection, SseEmitter emitter) {
        try {
            emitter.send(SseEmitter.event().comment("heartbeat"));
        } catch (IOException | IllegalStateException e) {
            sseEmitterRepository.delete(userId, connection);
            log.warn("SSE 이벤트 전송 실패로 연결을 정리했습니다. userId={}, connectionId={}", userId, connection.id(), e);
        }
    }
}
