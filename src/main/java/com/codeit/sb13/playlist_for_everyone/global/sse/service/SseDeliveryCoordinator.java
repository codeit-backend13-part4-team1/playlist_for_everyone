package com.codeit.sb13.playlist_for_everyone.global.sse.service;

import com.codeit.sb13.playlist_for_everyone.global.sse.event.Connection;
import com.codeit.sb13.playlist_for_everyone.global.sse.history.SseHistoryPosition;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Component
@RequiredArgsConstructor
public class SseDeliveryCoordinator {

    private final Map<ConnectionKey, ConnectionState> connectionStates = new ConcurrentHashMap<>();

    private final SseRecoveryService recoveryService;

    public void register(UUID userId, Connection connection, SseEmitter emitter){
        ConnectionKey key = new ConnectionKey(userId, connection);
        connectionStates.put(key, new ConnectionState(emitter));
    }

    public void recover(UUID userId, Connection connection, UUID lastEventId){
        ConnectionKey key = new ConnectionKey(userId, connection);
        ConnectionState state = connectionStates.get(key);
        if (state == null) return;
        state.lastPosition = recoveryService.recover(userId, connection, state.emitter, lastEventId);
        state.recovered = true;
        if (state.pendingDelivery){
            state.pendingDelivery = false;
            requestDelivery(userId, connection);
        }
    }

    public void remove(UUID userId, Connection connection){
        ConnectionKey key = new ConnectionKey(userId, connection);
        connectionStates.remove(key);
    }

    public void requestDelivery(UUID userId, Connection connection){
        ConnectionKey key = new ConnectionKey(userId, connection);
        ConnectionState state = connectionStates.get(key);
        if (state == null){
            return;
        }

        if (!state.recovered){
            state.pendingDelivery = true;
            return;
        }

        state.lastPosition = recoveryService.recoverAfter(userId, connection, state.emitter, state.lastPosition);
    }

    private record ConnectionKey(
            UUID userId,
            Connection connection
    ){

    }

    private static class ConnectionState {
        private final SseEmitter emitter;
        private boolean recovered;
        private boolean pendingDelivery;
        private SseHistoryPosition lastPosition;

        private ConnectionState(SseEmitter emitter){
            this.emitter = emitter;
        }
    }


}
