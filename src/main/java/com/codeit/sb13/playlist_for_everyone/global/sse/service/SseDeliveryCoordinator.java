package com.codeit.sb13.playlist_for_everyone.global.sse.service;

import com.codeit.sb13.playlist_for_everyone.global.sse.config.SseRetryProperties;
import com.codeit.sb13.playlist_for_everyone.global.sse.event.Connection;
import com.codeit.sb13.playlist_for_everyone.global.sse.history.SseHistoryPosition;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class SseDeliveryCoordinator {

    private final Map<ConnectionKey, ConnectionState> connectionStates = new ConcurrentHashMap<>();

    private final SseRecoveryService recoveryService;
    private final TaskScheduler retryTaskScheduler;
    private final SseRetryProperties retryProperties;

    public SseDeliveryCoordinator(
            SseRecoveryService recoveryService,
            @Qualifier("sseRetryTaskScheduler")
            TaskScheduler retryTaskScheduler,
            SseRetryProperties retryProperties) {
        this.recoveryService = recoveryService;
        this.retryTaskScheduler = retryTaskScheduler;
        this.retryProperties = retryProperties;
    }

    public void register(UUID userId, Connection connection, SseEmitter emitter){
        ConnectionKey key = new ConnectionKey(userId, connection);
        connectionStates.put(key, new ConnectionState(emitter));
    }

    public void recover(UUID userId, Connection connection, UUID lastEventId){
        ConnectionKey key = new ConnectionKey(userId, connection);
        ConnectionState state = connectionStates.get(key);
        if (state == null) return;

        SseHistoryPosition recoveredPosition = recoveryService
                .recover(userId, connection, state.emitter, lastEventId);
        boolean hasPendingDelivery;

        synchronized (state){
            if(connectionStates.get(key) != state){
                return;
            }
            state.lastPosition = recoveredPosition;
            state.recovered = true;

            hasPendingDelivery = state.pendingDelivery;
            state.pendingDelivery = false;
        }

        if (hasPendingDelivery){
            requestDelivery(userId, connection);
        }
    }

    public void remove(UUID userId, Connection connection){
        ConnectionKey key = new ConnectionKey(userId, connection);
        ConnectionState state = connectionStates.get(key);

        if (state == null) return;

        synchronized (state){
            if(connectionStates.remove(key, state)){
                state.pendingDelivery = false;
            }
        }
    }

    public void requestDelivery(UUID userId, Connection connection){
        ConnectionKey key = new ConnectionKey(userId, connection);
        ConnectionState state = connectionStates.get(key);

        if (state == null){
            return;
        }

        synchronized (state){
            if (connectionStates.get(key) != state){
                return;
            }

            if (!state.recovered || state.delivering){
                state.pendingDelivery = true;
                return;
            }

            state.delivering = true;
        }

        boolean deliveryReleased = false;

        try {
            while (true){
                SseHistoryPosition afterPosition;
                synchronized (state){
                    if (connectionStates.get(key) != state){
                        return;
                    }
                    state.pendingDelivery = false;
                    afterPosition = state.lastPosition;
                }
                SseHistoryPosition nextPosition = recoveryService.recoverAfter(userId,
                        connection,
                        state.emitter,
                        afterPosition);

                synchronized (state){
                    if (connectionStates.get(key) != state){
                        return;
                    }
                    state.lastPosition = nextPosition;

                    if (!state.pendingDelivery){
                        state.delivering = false;
                        deliveryReleased = true;
                        return;
                    }
                }
            }
        } finally {
            if (!deliveryReleased){
                synchronized (state){
                    state.delivering = false;
                }
            }
        }
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
        private boolean delivering;
        private SseHistoryPosition lastPosition;

        private ConnectionState(SseEmitter emitter){
            this.emitter = emitter;
        }
    }


}
