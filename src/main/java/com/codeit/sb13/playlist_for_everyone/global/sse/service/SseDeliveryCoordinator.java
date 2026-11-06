package com.codeit.sb13.playlist_for_everyone.global.sse.service;

import com.codeit.sb13.playlist_for_everyone.global.sse.event.Connection;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Component
@RequiredArgsConstructor
public class SseDeliveryCoordinator {

    private final Map<ConnectionKey, SseEmitter> registeredEmitters = new ConcurrentHashMap<>();

    private final SseRecoveryService recoveryService;

    public void register(UUID userId, Connection connection, SseEmitter emitter){
        ConnectionKey key = new ConnectionKey(userId, connection);
        registeredEmitters.put(key, emitter);
    }

    public void recover(UUID userId, Connection connection, UUID lastEventId){
        ConnectionKey key = new ConnectionKey(userId, connection);
        SseEmitter emitter = registeredEmitters.get(key);
        if (emitter == null) return;
        recoveryService.recover(userId, connection, emitter, lastEventId);
    }

    public void remove(UUID userId, Connection connection){
        ConnectionKey key = new ConnectionKey(userId, connection);
        registeredEmitters.remove(key);
    }

    public void requestDelivery(UUID userId, Connection connection){

    }

    private record ConnectionKey(UUID userId, Connection connection){

    }


}
