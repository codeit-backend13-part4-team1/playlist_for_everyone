package com.codeit.sb13.playlist_for_everyone.global.sse.service;

import com.codeit.sb13.playlist_for_everyone.global.sse.config.SseRetryProperties;
import com.codeit.sb13.playlist_for_everyone.global.sse.event.Connection;
import com.codeit.sb13.playlist_for_everyone.global.sse.history.SseHistoryPosition;
import com.codeit.sb13.playlist_for_everyone.global.sse.repository.SseEmitterRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import org.springframework.web.util.DisconnectedClientHelper;

import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;

/**
 * 한 연결의 초기 복구와 후속 전송이 같은 처리 위치를 공유하도록 조정한다.
 * 연결마다 별도 state를 가지므로 같은 사용자의 다른 탭도 독립적으로 진행한다.
 *
 * <p>synchronized(state)는 상태 전환과 로컬 저장소 등록·조회를 보호한다. 같은 연결의 작업만 이 잠금을 공유한다.
 * Redis·DB 조회와 HTTP 전송은 잠금 밖에서 실행하고 delivering으로 후속 전송을 겹치지 않게 한다. 작업 중 신호는
 * 목록에 쌓지 않고 pendingDelivery 하나로 합쳐, 완료 후 갱신된 위치에서 다시 조회한다.
 *
 * <p>연결 상태에는 처리 위치·전송 여부·예약만 유지한다. emitter는 로컬 저장소에서 조회한다.
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
    private final SseEmitterRepository emitterRepository;

    public SseDeliveryCoordinator(
            SseRecoveryService recoveryService,
            @Qualifier("sseRetryTaskScheduler")
            TaskScheduler retryTaskScheduler,
            SseRetryProperties retryProperties,
            SseConnectionCloser connectionCloser,
            SseEmitterRepository emitterRepository) {
        this.recoveryService = recoveryService;
        this.retryTaskScheduler = retryTaskScheduler;
        this.retryProperties = retryProperties;
        this.connectionCloser = connectionCloser;
        this.emitterRepository = emitterRepository;
    }

    /**
     * 새 연결의 상태 생성과 로컬 emitter 등록을 묶는다. 초기 복구 전 도착한 신호는 상태에 보류한다.
     * 상태를 먼저 공개하되 같은 state의 잠금을 유지하여, 조정기를 통한 조회는 저장 완료까지 기다리게 한다.
     * 저장 실패 시 이번에 추가한 상태만 취소한다. 기존 저장소 항목을 지우거나 HTTP 종료를 요청하지 않는다.
     *
     * @throws IllegalStateException 상태 또는 저장소에 같은 사용자·연결 키가 이미 등록된 경우
     */
    public void register(UUID userId, Connection connection, SseEmitter emitter){
        ConnectionKey key = new ConnectionKey(userId, connection);
        ConnectionState state = new ConnectionState(retryProperties.initialDelay());
        synchronized (state) {
            if (connectionStates.putIfAbsent(key, state) != null) {
                throw new IllegalStateException("이미 등록된 SSE 연결입니다.");
            }
            try {
                emitterRepository.save(userId, connection, emitter);
            } catch (RuntimeException failure) {
                removeConnectionState(key, state);
                throw failure;
            }
        }
    }

    /**
     * 초기 스냅샷 복구가 성공한 뒤 끝 위치를 기록하고 보류된 신호를 처리한다.
     * 초기 조회 실패는 여기서 재시도하지 않고 연결 생성 경로로 전달한다.
     * 상태·emitter가 없으면 조회하지 않는다. 조회 중 정리된 상태에는 복구 결과를 다시 기록하지 않는다.
     * 복구 서비스에는 사용자·emitter·커서를 전달하고, 연결 ID에 따른 상태 관리는 여기서 담당한다.
     */
    public void recover(UUID userId, Connection connection, UUID lastEventId){
        ConnectionKey key = new ConnectionKey(userId, connection);
        ConnectionState state = connectionStates.get(key);
        if (state == null) return;

        Optional<SseEmitter> emitter = findEmitter(key, state);
        if (emitter.isEmpty()) return;

        SseHistoryPosition recoveredPosition = recoveryService
                .recover(userId, emitter.get(), lastEventId);
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
            requestDelivery(key, state);
        }
    }

    /**
     * 브로커 payload를 직접 보내는 대신 마지막 처리 위치 이후의 저장된 이력을 전송한다.
     * 이미 복구·전송·재시도가 진행 중이면 신호만 보류한다. 정상 빈 결과도 성공으로 처리한다.
     * 두 종류의 일시적 데이터 접근 실패만 예약하며, 그 외 실패는 호출 경로에서 정리한다.
     */
    public void requestDelivery(UUID userId, Connection connection){
        ConnectionKey key = new ConnectionKey(userId, connection);
        ConnectionState state = connectionStates.get(key);
        if (state == null) {
            return;
        }

        requestDelivery(key, state);
    }

    /**
     * 진입 시 확보한 상태로 전송을 이어간다. 예약 작업도 이 경로를 사용해 키로 새 상태를 다시 선택하지 않는다.
     * 각 조회가 끝날 때 보류 신호를 확인하여, 조회 중 도착한 이벤트도 갱신된 위치부터 다시 읽는다.
     */
    private void requestDelivery(ConnectionKey key, ConnectionState state) {
        UUID userId = key.userId();
        Connection connection = key.connection();
        synchronized (state){
            if (findEmitter(key, state).isEmpty()) return;

            if (!state.recovered
                    || state.delivering
                    || state.retryFuture != null){
                state.pendingDelivery = true;
                return;
            }

            state.delivering = true;
        }

        // 잠금 안에서 전송권을 이미 해제했다면 finally가 새 작업의 delivering을 덮어쓰지 않게 한다.
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
                // 매 회차 저장소의 연결을 확인한다. 확보한 뒤 정리되는 경우 진행 중인 I/O까지 취소하지는 못한다.
                Optional<SseEmitter> emitter = findEmitter(key, state);
                if (emitter.isEmpty()) return;
                SseHistoryPosition nextPosition = recoveryService.recoverAfter(userId,
                        connection,
                        emitter.get(),
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
            // 후속 이력·필터 조회의 두 일시적 실패만 예약한다. 실패한 조회의 처리 위치는 진행하지 않는다.
            Optional<SseEmitter> emitterToClose = Optional.empty();
            synchronized (state){
                if (findEmitter(key, state).isEmpty()) return;
                if (!tryScheduleRetry(key,state)){
                   emitterToClose = detachState(key, state);
                }
                state.delivering =false;
                deliveryReleased =true;
            }
            if (emitterToClose.isPresent()){
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
                connectionCloser.close(userId, connection, emitterToClose.get());
            }
        } finally {
            if (!deliveryReleased){
                synchronized (state){
                    state.delivering = false;
                }
            }
        }
    }

    /**
     * 상태와 예약을 먼저 정리한 뒤, 저장소에서 확보한 emitter에 HTTP 종료와 참조 삭제를 요청한다.
     * 조회·필터 실패처럼 응답이 여전히 열려 있을 수 있는 경우에 사용하며 complete는 상태 잠금 밖에서 호출한다.
     */
    public void closeConnection(UUID userId, Connection connection){
        detachConnection(new ConnectionKey(userId, connection))
                .ifPresent(emitter -> connectionCloser.close(userId, connection, emitter));
    }

    /**
     * 상태·예약·저장소 참조만 정리하고 HTTP 종료는 요청하지 않는다. 완료·오류 콜백 등에서 사용한다.
     * 저장소에 emitter가 이미 없어도 남아 있는 상태·예약은 제거하며, Redis 이력에는 영향을 주지 않는다.
     */
    public void cleanupConnection(UUID userId, Connection connection){
        if (detachConnection(new ConnectionKey(userId, connection)).isPresent()) {
            connectionCloser.cleanUp(userId, connection);
        }
    }

    /**
     * 등록 완료를 기다린 뒤 실제 emitter 보관처인 저장소에서 조회한다.
     * 상태가 바뀌었거나 저장소 연결이 없으면 빈 결과로 전송을 중단한다. 후자는 남은 상태·예약도 정리한다.
     * 반환된 참조가 이후에도 살아 있다는 보장은 없으므로 전송 단계의 예외 처리는 별도로 필요하다.
     */
    private Optional<SseEmitter> findEmitter(ConnectionKey key, ConnectionState state) {
        synchronized (state) {
            if (connectionStates.get(key) != state) return Optional.empty();
            Optional<SseEmitter> emitter = emitterRepository.findByUserIdAndConnection(key.userId(), key.connection());
            if (emitter.isEmpty()) removeConnectionState(key, state);
            return emitter;
        }
    }

    /**
     * 자신이 확보한 상태와 현재 상태가 일치할 때만 상태·예약을 제거하고 종료 대상 emitter를 반환한다.
     * 저장소 참조 삭제와 HTTP 종료는 여기서 하지 않는다. 호출자가 잠금을 벗어난 뒤 closer에 위임한다.
     */
    private Optional<SseEmitter> detachState(ConnectionKey key, ConnectionState state) {
        synchronized (state) {
            if (connectionStates.get(key) != state) return Optional.empty();
            Optional<SseEmitter> emitter = emitterRepository.findByUserIdAndConnection(key.userId(), key.connection());
            removeConnectionState(key, state);
            return emitter;
        }
    }

    /** 상태가 이미 없어도 저장소 참조가 남아 있다면 정리 대상으로 반환한다. 호출자는 연결 ID를 재사용하지 않아야 한다. */
    private Optional<SseEmitter> detachConnection(ConnectionKey key) {
        ConnectionState state = connectionStates.get(key);
        if (state == null) {
            return emitterRepository.findByUserIdAndConnection(key.userId(), key.connection());
        }
        return detachState(key, state);
    }

    private record ConnectionKey(
            UUID userId,
            Connection connection
    ){

    }

    /** 이력 목록을 캐시하지 않는 연결 단위 상태. pendingDelivery는 개수가 아니라 재조회 필요 여부다. */
    private static class ConnectionState {
        // 초기 복구가 끝나야 후속 전송이 동일한 처리 위치에서 출발할 수 있다.
        private boolean recovered;
        // 복구·전송·재시도 중 신호를 여러 번 받아도 목록을 쌓지 않고 한 번의 재조회 요청으로 합친다.
        private boolean pendingDelivery;
        // 잠금을 풀고 I/O를 수행하는 동안에도 같은 연결의 후속 조회가 겹치지 않게 한다.
        private boolean delivering;
        // 브라우저 수신 확인이 아니라 마지막으로 처리한 원본 이력의 위치다. 필터 제외 항목도 포함한다.
        private SseHistoryPosition lastPosition;
        // 최초 시도를 제외한 예약 횟수와 다음 대기 간격. 정상 빈 조회를 포함한 성공 시 초기화한다.
        private int retryCount;
        private Duration nextRetryDelay;
        // null이 아니면 대기 중인 예약이 있다. 예약 실행·취소 시 비워 중복 예약을 방지한다.
        private ScheduledFuture<?> retryFuture;

        private ConnectionState(Duration initialRetryDelay){
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

    /**
     * 예약이 캡처한 상태와 유효한 예약이 남아 있을 때만 실행한다. 예약을 소비한 뒤 공통 전송 경로를 호출한다.
     * 두 일시적 조회 예외는 그 경로에서 다시 예약한다. 밖으로 전달된 그 외 RuntimeException은
     * 상태·예약 정리와 HTTP 종료로 처리하여, 전송은 멈췄지만 응답만 열린 연결을 남기지 않는다.
     */
    private void runRetry(ConnectionKey key, ConnectionState state){
        synchronized (state){
            if (connectionStates.get(key) != state
                    || state.retryFuture == null){
                return;
            }
            state.retryFuture = null;
        }
        try {
            requestDelivery(key, state);
        } catch (RuntimeException failure){
            Optional<SseEmitter> emitter = detachState(key, state);
            if (emitter.isEmpty()) return;
            logRetryDeliveryFailure(key, failure);
            connectionCloser.close(key.userId(), key.connection(), emitter.get());
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

    /**
     * Map에 같은 state가 남아 있을 때만 제거하고 예약을 취소한다. 오래된 작업은 현재 상태를 제거하지 못한다.
     * 저장 실패의 상태 취소와 연결 정리가 함께 쓰는 내부 도구다. emitter 저장소 삭제·HTTP 종료는 수행하지 않는다.
     */
    private boolean removeConnectionState(ConnectionKey key, ConnectionState state){
        synchronized (state){
            if (!connectionStates.remove(key, state)){
                return false;
            }
            state.pendingDelivery = false;
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
