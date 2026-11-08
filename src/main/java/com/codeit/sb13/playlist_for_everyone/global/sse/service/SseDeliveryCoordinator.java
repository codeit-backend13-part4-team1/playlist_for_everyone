package com.codeit.sb13.playlist_for_everyone.global.sse.service;

import com.codeit.sb13.playlist_for_everyone.global.sse.config.SseRetryProperties;
import com.codeit.sb13.playlist_for_everyone.global.sse.event.Connection;
import com.codeit.sb13.playlist_for_everyone.global.sse.history.SseHistoryPosition;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;

@Component
public class SseDeliveryCoordinator {

    private final Map<ConnectionKey, ConnectionState> connectionStates = new ConcurrentHashMap<>();

    private final SseRecoveryService recoveryService;
    private final TaskScheduler retryTaskScheduler;
    private final SseRetryProperties retryProperties;
    private final SseConnectionCloser connectionCloser;

    public SseDeliveryCoordinator(
            SseRecoveryService recoveryService,
            @Qualifier("sseRetryTaskScheduler")
            TaskScheduler retryTaskScheduler,
            SseRetryProperties retryProperties, SseConnectionCloser connectionCloser) {
        this.recoveryService = recoveryService;
        this.retryTaskScheduler = retryTaskScheduler;
        this.retryProperties = retryProperties;
        this.connectionCloser = connectionCloser;
    }

    public void register(UUID userId, Connection connection, SseEmitter emitter){
        ConnectionKey key = new ConnectionKey(userId, connection);
        connectionStates.put(key, new ConnectionState(emitter, retryProperties.initialDelay()));
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
                cancelRetry(state);
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

            if (!state.recovered
                    || state.delivering
                    || state.retryFuture != null){
                state.pendingDelivery = true;
                return;
            }

            state.delivering = true;
        }

        boolean deliveryReleased = false;

        try {
            while (true) {
                SseHistoryPosition afterPosition;
                synchronized (state) {
                    if (connectionStates.get(key) != state) {
                        return;
                    }
                    state.pendingDelivery = false;
                    afterPosition = state.lastPosition;
                }
                SseHistoryPosition nextPosition = recoveryService.recoverAfter(userId,
                        connection,
                        state.emitter,
                        afterPosition);

                synchronized (state) {
                    if (connectionStates.get(key) != state) {
                        return;
                    }
                    state.lastPosition = nextPosition;
                    state.retryCount = 0;
                    state.nextRetryDelay = retryProperties.initialDelay();

                    if (!state.pendingDelivery) {
                        state.delivering = false;
                        deliveryReleased = true;
                        return;
                    }
                }
            }
        } catch (DataAccessResourceFailureException failure){
            boolean shouldClose = false;
            synchronized (state){
                if (connectionStates.get(key) != state){
                    return;
                }
                if (!tryScheduleRetry(key,state)){
                   if (connectionStates.remove(key, state)){
                       state.pendingDelivery = false;
                       cancelRetry(state);
                       shouldClose = true;
                   }
                }
                state.delivering =false;
                deliveryReleased =true;
            }
            if (shouldClose){
                connectionCloser.close(userId, connection, state.emitter);
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
        private int retryCount;
        private Duration nextRetryDelay;
        private ScheduledFuture<?> retryFuture;

        private ConnectionState(SseEmitter emitter, Duration initialRetryDelay){
            this.emitter = emitter;
            this.nextRetryDelay = initialRetryDelay;
        }
    }

    private void cancelRetry(ConnectionState state){
        ScheduledFuture<?> retryFuture = state.retryFuture;
        state.retryFuture = null;
        if (retryFuture != null){
            retryFuture.cancel(false);
        }
    }

    private void runRetry(ConnectionKey key, ConnectionState state){
        synchronized (state){
            if (connectionStates.get(key) != state
                    || state.retryFuture == null){
                return;
            }
            state.retryFuture = null;
        }
        requestDelivery(key.userId, key.connection);
    }

    private boolean tryScheduleRetry(ConnectionKey key, ConnectionState state){
        synchronized (state){
            if (connectionStates.get(key) != state){
                return false;
            }

            if (state.retryFuture != null){
                return true;
            }

            if (state.retryCount >= retryProperties.maxRetries()){
                return false;
            }

            Duration delay = state.nextRetryDelay;
            Duration maxDelay = retryProperties.maxDelay();
            Duration nextDelay = delay.compareTo(maxDelay.minus(delay)) >= 0 ? maxDelay : delay.multipliedBy(2);

            state.retryFuture = retryTaskScheduler.schedule(() -> runRetry(key, state),
                    retryTaskScheduler.getClock().instant().plus(delay));

            state.retryCount++;
            state.nextRetryDelay = nextDelay;
            return true;

        }
    }


}
