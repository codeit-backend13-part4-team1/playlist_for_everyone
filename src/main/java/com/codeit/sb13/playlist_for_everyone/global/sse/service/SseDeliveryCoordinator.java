package com.codeit.sb13.playlist_for_everyone.global.sse.service;

import com.codeit.sb13.playlist_for_everyone.global.sse.config.SseRetryProperties;
import com.codeit.sb13.playlist_for_everyone.global.sse.event.Connection;
import com.codeit.sb13.playlist_for_everyone.global.sse.history.SseHistoryPosition;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import org.springframework.web.util.DisconnectedClientHelper;

import java.io.UncheckedIOException;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;

/**
 * 한 연결의 초기 복구와 후속 전송이 같은 처리 위치를 공유하도록 조정한다.
 * 연결마다 별도 state를 가지므로 같은 사용자의 다른 탭도 독립적으로 진행한다.
 *
 * <p>synchronized(state)는 상태 전환만 보호한다. Redis·DB 조회와 HTTP 전송은 잠금 밖에서
 * 실행하고 delivering으로 같은 연결의 후속 전송을 겹치지 않게 한다. 작업 중 들어온 신호는
 * 목록에 쌓지 않고 pendingDelivery 하나로 합쳐, 완료 후 갱신된 위치에서 다시 조회한다.
 *
 * <p>연결 상태에는 emitter·처리 위치·예약만 유지하고 조회한 이력 목록은 보관하지 않는다.
 * 복구 중 제거되었거나 늦게 실행된 작업은 Map의 state 동일성으로 구분한다.
 * 이미 실행 중인 조회·send의 즉시 중단까지 보장하는 구조는 아니다.
 */
@Component
@Slf4j
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

    /** emitter 저장 전에 호출하여, 초기 복구 전 들어오는 신호도 보류할 수 있게 한다. */
    public void register(UUID userId, Connection connection, SseEmitter emitter){
        ConnectionKey key = new ConnectionKey(userId, connection);
        connectionStates.put(key, new ConnectionState(emitter, retryProperties.initialDelay()));
    }

    /**
     * 초기 스냅샷 복구가 성공한 뒤 끝 위치를 기록하고 보류된 신호를 처리한다.
     * 초기 조회 실패는 여기서 재시도하지 않고 연결 생성 경로로 전달한다.
     */
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

    /** 연결 상태와 대기 중인 재시도를 정리한다. emitter 저장소와 Redis 이력 삭제는 수행하지 않는다. */
    public void remove(UUID userId, Connection connection){
        ConnectionKey key = new ConnectionKey(userId, connection);
        ConnectionState state = connectionStates.get(key);

        if (state == null) return;

        removeConnectionState(key, state);
    }

    /**
     * 브로커 payload를 직접 보내는 대신 마지막 처리 위치 이후의 저장된 이력을 전송한다.
     * 이미 복구·전송·재시도가 진행 중이면 신호만 보류한다. 정상 빈 결과도 성공으로 처리한다.
     * 두 종류의 일시적 데이터 접근 실패만 예약하며, 그 외 실패는 호출 경로에서 정리한다.
     */
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
                    // 필터로 제외된 이벤트도 처리 위치에 포함한다. 성공하면 다음 장애의 예산을 새로 시작한다.
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
        } catch (DataAccessResourceFailureException | QueryTimeoutException failure){
            boolean shouldClose = false;
            synchronized (state){
                if (connectionStates.get(key) != state){
                    return;
                }
                if (!tryScheduleRetry(key,state)){
                   shouldClose = removeConnectionState(key, state);
                }
                state.delivering =false;
                deliveryReleased =true;
            }
            if (shouldClose){
                log.warn(
                        """
                        SSE 후속 이력 조회 재시도를 소진하여 연결을 종료합니다.
                        userId={}, connectionId={}, maxRetries={}
                        """,
                        userId,
                        connection.id(),
                        retryProperties.maxRetries(),
                        failure
                );
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

    /** 이력 목록을 캐시하지 않는 연결 단위 상태. pendingDelivery는 개수가 아니라 재조회 필요 여부다. */
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

    /** false는 이미 실행 중인 스레드를 interrupt하지 않는다는 뜻이다. 늦은 작업은 별도로 상태를 확인한다. */
    private void cancelRetry(ConnectionState state){
        ScheduledFuture<?> retryFuture = state.retryFuture;
        state.retryFuture = null;
        if (retryFuture != null){
            retryFuture.cancel(false);
        }
    }

    /** 예약을 소비한 뒤 기존 전송 경로를 재사용한다. 예약 작업의 I/O·상태 오류는 여기서 연결을 정리한다. */
    private void runRetry(ConnectionKey key, ConnectionState state){
        synchronized (state){
            if (connectionStates.get(key) != state
                    || state.retryFuture == null){
                return;
            }
            state.retryFuture = null;
        }
        try {
            requestDelivery(key.userId, key.connection);
        } catch (UncheckedIOException | IllegalStateException failure){
            if (!removeConnectionState(key, state)){
                return;
            }
            logRetryDeliveryFailure(key, failure);
            connectionCloser.close(key.userId, key.connection, state.emitter);
        }
    }

    /**
     * 최초 실패 이후의 예약 횟수를 센다. 기본 3회라면 최초 시도를 포함해 최대 네 번 조회한다.
     * 간격은 두 배씩 늘리되 최대 간격으로 제한한다. 예약 대기 중 추가 신호로 횟수를 소모하지 않는다.
     */
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

    /** 예상한 state만 제거하여 정리 주체를 하나로 정한다. 공유 이력과 같은 사용자의 다른 연결은 유지한다. */
    private boolean removeConnectionState(ConnectionKey key, ConnectionState state){
        synchronized (state){
            if (!connectionStates.remove(key, state)){
                return false;
            }
            state.pendingDelivery =false;
            cancelRetry(state);
            return true;
        }
    }

    private void logRetryDeliveryFailure(ConnectionKey key, RuntimeException failure){
        if (DisconnectedClientHelper.isClientDisconnectedException(failure)){
            log.debug(
                    "SSE 예약 재시도 중 클라이언트 단절로 연결을 정리합니다. userId={}, connectionId={}",
                    key.userId(),
                    key.connection().id()
            );
            return;
        }
        log.warn(
                "SSE 예약 재시도 중 전송에 실패하여 연결을 종료합니다. userId={}, connectionId={}",
                key.userId(),
                key.connection().id(),
                failure
        );
    }


}
