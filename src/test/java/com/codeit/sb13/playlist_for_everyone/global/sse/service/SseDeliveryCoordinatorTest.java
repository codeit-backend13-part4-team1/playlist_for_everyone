package com.codeit.sb13.playlist_for_everyone.global.sse.service;

import com.codeit.sb13.playlist_for_everyone.global.sse.config.SseRetryProperties;
import com.codeit.sb13.playlist_for_everyone.global.sse.event.Connection;
import com.codeit.sb13.playlist_for_everyone.global.sse.history.SseHistoryPosition;
import com.codeit.sb13.playlist_for_everyone.global.sse.repository.SseEmitterRepository;
import com.codeit.sb13.playlist_for_everyone.global.sse.repository.impl.InMemorySseEmitterRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("SSE 복구·실시간 전송 조정")
class SseDeliveryCoordinatorTest {

    @Mock
    private SseRecoveryService recoveryService;

    @Mock
    private SseEmitter emitter;

    @Mock
    private TaskScheduler retryTaskScheduler;

    @Mock
    private ScheduledFuture<?> retryFuture;

    @Mock SseConnectionCloser connectionCloser;

    private SseDeliveryCoordinator coordinator;
    private SseEmitterRepository repository;
    private final UUID userId = UUID.randomUUID();
    private final Connection connection = Connection.create();
    private final UUID lastEventId = UUID.randomUUID();
    private final SseRetryProperties retryProperties = new SseRetryProperties(
            Duration.ofSeconds(1),
            Duration.ofSeconds(30),
            3
    );

    @BeforeEach
    void setUp(){
        repository = new InMemorySseEmitterRepository();
        coordinator = new SseDeliveryCoordinator(
                recoveryService,
                retryTaskScheduler,
                retryProperties,
                connectionCloser, repository);
    }

    @Test
    @DisplayName("등록한 연결의 에미터와 사용자·마지막 이벤트 ID로 복구를 요청한다.")
    void recoverDelegatesWithRegisteredEmitterAndLastEventId(){
        coordinator.register(userId, connection, emitter);
        coordinator.recover(userId, connection, lastEventId);

        verify(recoveryService).recover(eq(userId), same(emitter), eq(lastEventId));
    }

    @Test
    @DisplayName("마지막 이벤트 ID가 없어도 등록한 연결의 복구를 요청한다.")
    void recoverDelegatesWithoutLastEventId(){
        coordinator.register(userId, connection, emitter);
        coordinator.recover(userId, connection, null);

        verify(recoveryService)
                .recover(eq(userId), same(emitter), isNull());
    }

    @Test
    @DisplayName("같은 사용자의 두 연결은 각자의 에미터와 마지막 이벤트 ID로 복구를 요청한다.")
    void recoversConnectionsOfSameUserIndependently(){
        Connection secondConnection = Connection.create();
        SseEmitter secondEmitter = mock(SseEmitter.class);
        UUID secondLastEventId = UUID.randomUUID();

        coordinator.register(userId, connection, emitter);
        coordinator.register(userId, secondConnection, secondEmitter);

        coordinator.recover(userId, connection, lastEventId);
        coordinator.recover(userId, secondConnection, secondLastEventId);

        verify(recoveryService)
                .recover(eq(userId), same(emitter), eq(lastEventId));
        verify(recoveryService)
                .recover(eq(userId), same(secondEmitter), eq(secondLastEventId));
    }

    @Test
    @DisplayName("정리한 연결은 복구하지 않고 같은 사용자의 다른 연결은 복구한다.")
    void cleanupPreventsRecoveryOnlyForTargetConnection(){
        Connection secondConnection = Connection.create();
        SseEmitter secondEmitter = mock(SseEmitter.class);
        UUID secondLastEventId = UUID.randomUUID();

        coordinator.register(userId, connection, emitter);
        coordinator.register(userId, secondConnection, secondEmitter);

        coordinator.cleanupConnection(userId, connection);
        coordinator.recover(userId, connection, lastEventId);

        coordinator.recover(userId, secondConnection, secondLastEventId);

        verify(recoveryService)
                .recover(eq(userId), same(secondEmitter), eq(secondLastEventId));

        verifyNoMoreInteractions(recoveryService);
        verify(connectionCloser).cleanUp(userId, connection);
        verifyNoMoreInteractions(connectionCloser);
    }

    @Test
    @DisplayName("복구가 완료된 위치부터 해당 연결의 후속 전송을 요청한다.")
    void requestsDeliveryAfterRecoveredPosition(){
        SseHistoryPosition upperBound = createHistoryPosition();

        when(recoveryService.recover(userId, emitter, lastEventId))
                .thenReturn(upperBound);

        coordinator.register(userId, connection, emitter);
        coordinator.recover(userId, connection, lastEventId);
        coordinator.requestDelivery(userId, connection);

        verify(recoveryService).recoverAfter(eq(userId), eq(connection), same(emitter), eq(upperBound));
    }

    @Test
    @DisplayName("복구 완료 위치가 null이어도 해당 연결의 후속 전송을 요청한다.")
    void requestsDeliveryWhenRecoveredPositionIsNull(){
        when(recoveryService.recover(userId, emitter, lastEventId))
                .thenReturn(null);

        coordinator.register(userId, connection, emitter);
        coordinator.recover(userId, connection, lastEventId);
        coordinator.requestDelivery(userId, connection);

        verify(recoveryService)
                .recoverAfter(eq(userId), eq(connection), same(emitter), isNull());
    }

    @Test
    @DisplayName("후속 전송이 반환한 위치를 다음 전송 요청의 기준으로 사용한다.")
    void usesUpdatedPositionForNextDeliveryRequest(){
        SseHistoryPosition upperBound = createHistoryPosition();
        SseHistoryPosition nextPosition = createHistoryPosition("1000-1");

        when(recoveryService.recover(userId, emitter,lastEventId))
                .thenReturn(upperBound);

        when(recoveryService.recoverAfter(userId, connection, emitter, upperBound))
                .thenReturn(nextPosition);

        when(recoveryService.recoverAfter(userId, connection, emitter, nextPosition))
                .thenReturn(nextPosition);

        coordinator.register(userId, connection, emitter);
        coordinator.recover(userId, connection, lastEventId);

        coordinator.requestDelivery(userId, connection);
        coordinator.requestDelivery(userId, connection);

        ArgumentCaptor<SseHistoryPosition> positionCaptor = ArgumentCaptor.forClass(SseHistoryPosition.class);
        verify(recoveryService, times(2)).recoverAfter(
                eq(userId),
                eq(connection),
                same(emitter),
                positionCaptor.capture()
        );

        assertThat(positionCaptor.getAllValues())
                .containsExactly(upperBound, nextPosition);
    }

    @Test
    @DisplayName("같은 사용자의 두 연결은 후속 전송위치를 독립적으로 관리한다.")
    void maintainsIndependentDeliveryPositionsForConnectionOfSameUser(){
        Connection secondConnection = Connection.create();
        SseEmitter secondEmitter = mock(SseEmitter.class);
        UUID secondLastEventId = UUID.randomUUID();

        SseHistoryPosition firstPosition = createHistoryPosition();
        SseHistoryPosition firstNextPosition = createHistoryPosition("1000-1");
        SseHistoryPosition secondPosition = createHistoryPosition("2000-0");

        when(recoveryService.recover(userId, emitter, lastEventId))
                .thenReturn(firstPosition);

        when(recoveryService.recover(userId, secondEmitter, secondLastEventId))
                .thenReturn(secondPosition);
        when(recoveryService.recoverAfter(userId, connection, emitter, firstPosition))
                .thenReturn(firstNextPosition);
        when(recoveryService.recoverAfter(userId, secondConnection, secondEmitter, secondPosition))
                .thenReturn(secondPosition);

        coordinator.register(userId, connection, emitter);
        coordinator.register(userId, secondConnection, secondEmitter);

        coordinator.recover(userId, connection,lastEventId);
        coordinator.recover(userId, secondConnection, secondLastEventId);

        coordinator.requestDelivery(userId, connection);
        coordinator.requestDelivery(userId, secondConnection);

        verify(recoveryService).recoverAfter(eq(userId), eq(connection), same(emitter), eq(firstPosition));
        verify(recoveryService).recoverAfter(eq(userId), eq(secondConnection), same(secondEmitter), eq(secondPosition));

    }

    @Test
    @DisplayName("연결 등록 후 복구를 시작하기 전에는 후속 전송을 즉시 실행하지 않는다.")
    void doesNotDeliverBeforeRecoveryStarts(){
        coordinator.register(userId, connection, emitter);
        coordinator.requestDelivery(userId, connection);

        verifyNoInteractions(recoveryService);
    }

    @Test
    @DisplayName("제거한 연결은 후속 전송하지 않고 같은 사용자의 다른 연결은 전송한다.")
    void doesNotDeliverToRemovedConnectionAndKeepsOtherConnection(){
        Connection secondConnection = Connection.create();
        SseEmitter secondEmitter = mock(SseEmitter.class);
        UUID secondLastEventId = UUID.randomUUID();

        SseHistoryPosition firstPosition = createHistoryPosition();
        SseHistoryPosition secondPosition = createHistoryPosition("2000-0");

        when(recoveryService.recover(userId, emitter, lastEventId))
                .thenReturn(firstPosition);

        when(recoveryService.recover(userId, secondEmitter, secondLastEventId))
                .thenReturn(secondPosition);

        when(recoveryService.recoverAfter(userId, secondConnection, secondEmitter, secondPosition))
                .thenReturn(secondPosition);

        coordinator.register(userId, connection, emitter);
        coordinator.register(userId, secondConnection, secondEmitter);

        coordinator.recover(userId, connection, lastEventId);
        coordinator.recover(userId, secondConnection, secondLastEventId);

        coordinator.cleanupConnection(userId, connection);

        coordinator.requestDelivery(userId, connection);
        coordinator.requestDelivery(userId, secondConnection);

        verify(recoveryService, never() )
                .recoverAfter(eq(userId), eq(connection), any(), any());

        verify(recoveryService)
                .recoverAfter(eq(userId), eq(secondConnection), same(secondEmitter), eq(secondPosition));
        verify(connectionCloser).cleanUp(userId, connection);
        verifyNoMoreInteractions(connectionCloser);

    }

    @Test
    @DisplayName("복구 전에 받은 전송 요청은 복구 완료 후 해당 위치부터 처리한다.")
    void deliversPendingRequestAfterRecoveryCompletes(){
        SseHistoryPosition upperBound = createHistoryPosition();

        when(recoveryService.recover(userId, emitter, lastEventId))
                .thenReturn(upperBound);
        when(recoveryService.recoverAfter(userId, connection, emitter, upperBound))
                .thenReturn(upperBound);

        coordinator.register(userId, connection, emitter);
        coordinator.requestDelivery(userId, connection);

        verifyNoInteractions(recoveryService);

        coordinator.recover(userId, connection, lastEventId);

        verify(recoveryService)
                .recoverAfter(eq(userId), eq(connection), same(emitter), eq(upperBound));
    }

    @Test
    @DisplayName("복구 전에 받은 여러 전송 요청은 복구 완료 후 한 번으로 합쳐 처리한다.")
    void coalescesRequestsBeforeRecoveryIntoSingleDelivery(){
        SseHistoryPosition upperBound = createHistoryPosition();

        when(recoveryService.recover(userId, emitter, lastEventId))
                .thenReturn(upperBound);
        when(recoveryService.recoverAfter(userId, connection, emitter, upperBound))
                .thenReturn(upperBound);

        coordinator.register(userId, connection, emitter);

        coordinator.requestDelivery(userId, connection);
        coordinator.requestDelivery(userId, connection);
        coordinator.requestDelivery(userId, connection);

        verifyNoInteractions(recoveryService);

        coordinator.recover(userId, connection, lastEventId);
        verify(recoveryService).recover(userId, emitter, lastEventId);
        verify(recoveryService).recoverAfter(eq(userId), eq(connection), same(emitter), eq(upperBound));
        verifyNoMoreInteractions(recoveryService);
    }

    @Test
    @DisplayName("복구 진행 중 받은 전송 요청은 복구 완료 후 처리한다.")
    void deliversRequestReceivedDuringRecoveryAfterCompletion(){
        SseHistoryPosition upperBound = createHistoryPosition();

        when(recoveryService.recover(userId, emitter, lastEventId))
                .thenAnswer(invocation ->{
                    coordinator.requestDelivery(userId, connection);
                    verify(recoveryService, never())
                            .recoverAfter(any(), any(),any(),any());

                    return upperBound;
                });

        when(recoveryService.recoverAfter(userId, connection, emitter, upperBound))
                .thenReturn(upperBound);

        coordinator.register(userId, connection, emitter);
        coordinator.recover(userId, connection, lastEventId);

        verify(recoveryService)
                .recoverAfter(eq(userId), eq(connection), same(emitter), eq(upperBound));
    }

    @Test
    @DisplayName("초기 복구가 실패하면 보류 요청을 실행하거나 재시도를 예약하지 않고 원래 예외를 전달한다.")
    void propagatesRecoveryFailureWithoutDeliveringPendingRequest(){
        DataAccessResourceFailureException failure = new DataAccessResourceFailureException("테스트용 스냅샷 복구 실패");

        when(recoveryService.recover(userId, emitter, lastEventId))
                .thenThrow(failure);

        coordinator.register(userId, connection, emitter);
        coordinator.requestDelivery(userId, connection);

        assertThatThrownBy(() -> coordinator.recover(userId, connection, lastEventId))
                .isSameAs(failure);

        verify(recoveryService)
                .recover(userId, emitter, lastEventId);
        verify(recoveryService, never()).recoverAfter(any(), any(), any(), any());
        verifyNoInteractions(retryTaskScheduler);
    }

    @Test
    @DisplayName("후속 전송 중 받은 요청은 현재 전송 완료 후 갱신된 위치부터 처리한다.")
    void deliversRequestReceivedDuringDeliveryAfterCurrentDeliveryCompletes(){
        SseHistoryPosition upperBound = createHistoryPosition();
        SseHistoryPosition nextPosition = createHistoryPosition("1000-1");

        when(recoveryService.recover(userId, emitter, lastEventId))
                .thenReturn(upperBound);
        when(recoveryService.recoverAfter(userId, connection, emitter, upperBound))
                .thenAnswer(invocation -> {
                    coordinator.requestDelivery(userId, connection);
                    verify(recoveryService, times(1))
                            .recoverAfter(any(), any(), any(), any());
                    return nextPosition;
                })
                .thenReturn(nextPosition);

        when(recoveryService.recoverAfter(userId, connection, emitter, nextPosition))
                .thenReturn(nextPosition);

        coordinator.register(userId, connection, emitter);
        coordinator.recover(userId, connection, lastEventId);
        coordinator.requestDelivery(userId, connection);
        ArgumentCaptor<SseHistoryPosition> positionCaptor = ArgumentCaptor.forClass(SseHistoryPosition.class);

        verify(recoveryService, times(2))
                .recoverAfter(eq(userId), eq(connection), same(emitter), positionCaptor.capture());
        assertThat(positionCaptor.getAllValues())
                .containsExactly(upperBound, nextPosition);
    }

    @Test
    @DisplayName("후속 전송 중 받은 여러 요청은 현재 전송 완료 후 한 번으로 합쳐 처리한다.")
    void coalescesRequestsDuringDeliveryIntoSingleFollowingDelivery(){
        SseHistoryPosition upperBound = createHistoryPosition();
        SseHistoryPosition nextPosition = createHistoryPosition("1000-1");

        when(recoveryService.recover(userId, emitter, lastEventId))
                .thenReturn(upperBound);
        when(recoveryService.recoverAfter(userId, connection, emitter, upperBound))
                .thenAnswer(invocation -> {
                    coordinator.requestDelivery(userId, connection);
                    coordinator.requestDelivery(userId, connection);
                    coordinator.requestDelivery(userId, connection);
                    verify(recoveryService, times(1))
                            .recoverAfter(any(), any(), any(), any());
                    return nextPosition;
                })
                .thenReturn(nextPosition);

        when(recoveryService. recoverAfter(userId, connection, emitter, nextPosition))
                .thenReturn(nextPosition);

        coordinator.register(userId, connection, emitter);
        coordinator.recover(userId, connection, lastEventId);
        coordinator.requestDelivery(userId, connection);

        verify(recoveryService)
                .recover(userId, emitter, lastEventId);
        verify(recoveryService)
                .recoverAfter(eq(userId), eq(connection), same(emitter), eq(upperBound));
        verify(recoveryService)
                .recoverAfter(eq(userId), eq(connection), same(emitter),eq(nextPosition));
        verifyNoMoreInteractions(recoveryService);
    }

    @Test
    @DisplayName("SSE 이벤트 전송이 실패하면 보류 요청을 실행하거나 재시도를 예약하지 않고 원래 예외를 전달한다.")
    void propagatesEventSendFailureWithoutDeliveringPendingRequest(){
        SseHistoryPosition upperBound = createHistoryPosition();
        UncheckedIOException failure = new UncheckedIOException("테스트용 SSE 이벤트 전송 실패",
                new IOException("테스트용 연결 오류"));

        when(recoveryService.recover(userId, emitter, lastEventId))
                .thenReturn(upperBound);
        when(recoveryService.recoverAfter(userId, connection, emitter, upperBound))
                .thenAnswer(invocation -> {
                    coordinator.requestDelivery(userId, connection);
                    throw failure;
                })
                .thenReturn(upperBound);

        coordinator.register(userId, connection, emitter);
        coordinator.recover(userId, connection, lastEventId);

        assertThatThrownBy(() -> coordinator.requestDelivery(userId, connection))
                .isSameAs(failure);

        verify(recoveryService)
                .recover(userId, emitter, lastEventId);
        verify(recoveryService)
                .recoverAfter(userId, connection, emitter, upperBound);
        verifyNoMoreInteractions(recoveryService);
        verifyNoInteractions(retryTaskScheduler);
    }


    @ParameterizedTest(name = "{index}: {0}")
    @MethodSource("nonRetryableFailures")
    @DisplayName("예약 재시도 중 재시도 대상이 아닌 예외가 발생하면 연결을 종료하고 이후 요청과 늦은 작업을 무시한다.")
    void closesConnectionWhenNonRetryableFailureOccursDuringScheduledRetry(RuntimeException failure){
        SseHistoryPosition upperBound = createHistoryPosition();
        Instant now = Instant.parse("2026-11-07T00:00:00Z");
        DataAccessResourceFailureException historyFailure = new DataAccessResourceFailureException("테스트용 후속 이력 조회 실패");

        when(retryTaskScheduler.getClock())
                .thenReturn(Clock.fixed(now, ZoneOffset.UTC));
        doReturn(retryFuture)
                .when(retryTaskScheduler)
                .schedule(any(Runnable.class), any(Instant.class));
        when(recoveryService.recover(userId, emitter, lastEventId))
                .thenReturn(upperBound);
        when(recoveryService.recoverAfter(userId, connection, emitter, upperBound))
                .thenThrow(historyFailure)
                .thenThrow(failure);

        coordinator.register(userId, connection, emitter);
        coordinator.recover(userId, connection, lastEventId);

        assertThatCode(() -> coordinator.requestDelivery(userId, connection))
                .doesNotThrowAnyException();

        ArgumentCaptor<Runnable> taskCaptor = ArgumentCaptor.forClass(Runnable.class);
        verify(retryTaskScheduler).schedule(taskCaptor.capture(), any(Instant.class));
        verifyNoInteractions(connectionCloser);

        Runnable retryTask = taskCaptor.getValue();
        assertThatCode(retryTask::run).doesNotThrowAnyException();
        assertThatCode(()-> coordinator.requestDelivery(userId, connection))
                .doesNotThrowAnyException();
        assertThatCode(retryTask::run).doesNotThrowAnyException();

        verify(recoveryService)
                .recover(userId, emitter, lastEventId);
        verify(recoveryService, times(2))
                .recoverAfter(userId, connection, emitter, upperBound);
        verifyNoMoreInteractions(recoveryService);
        verify(retryTaskScheduler, times(1)).schedule(any(Runnable.class), any(Instant.class));
        verify(connectionCloser).close(userId, connection, emitter);
        verifyNoMoreInteractions(connectionCloser);
    }

    @Test
    @DisplayName("후속 전송 중 연결이 제거되면 보류한 요청을 실행하지 않는다.")
    void stopsPendingDeliveryWhenConnectionIsRemovedDuringDelivery(){
        SseHistoryPosition upperBound = createHistoryPosition();
        SseHistoryPosition nextPosition = createHistoryPosition("1000-1");

        when(recoveryService.recover(userId,emitter, lastEventId))
                .thenReturn(upperBound);
        when(recoveryService.recoverAfter(userId, connection, emitter, upperBound))
                .thenAnswer(invocation ->{
                    coordinator.requestDelivery(userId, connection);
                    coordinator.cleanupConnection(userId, connection);
                    return nextPosition;
                });

        coordinator.register(userId, connection, emitter);
        coordinator.recover(userId, connection, lastEventId);
        coordinator.requestDelivery(userId, connection);
        coordinator.requestDelivery(userId, connection);

        verify(recoveryService)
                .recover(userId, emitter, lastEventId);
        verify(recoveryService)
                .recoverAfter(eq(userId), eq(connection), same(emitter), eq(upperBound));
        verifyNoMoreInteractions(recoveryService);
        verify(connectionCloser).cleanUp(userId, connection);
        verifyNoMoreInteractions(connectionCloser);
    }

    @Test
    @DisplayName("초기 복구 중 연결이 제거되면 복구 완료 후 보류한 요청을 실행하지 않는다.")
    void  stopsPendingDeliveryWhenConnectionIsRemovedDuringRecovery(){
        SseHistoryPosition upperBound = createHistoryPosition();

        when(recoveryService.recover(userId, emitter, lastEventId))
                .thenAnswer(invocation -> {
                    coordinator.requestDelivery(userId, connection);
                    coordinator.cleanupConnection(userId, connection);
                    return upperBound;
                });

        coordinator.register(userId, connection, emitter);
        coordinator.recover(userId, connection, lastEventId);
        coordinator.requestDelivery(userId, connection);

        verify(recoveryService)
                .recover(eq(userId), same(emitter), eq(lastEventId));
        verify(recoveryService, never())
                .recoverAfter(any(), any(), any(), any());
        verifyNoMoreInteractions(recoveryService);
        verify(connectionCloser).cleanUp(userId, connection);
        verifyNoMoreInteractions(connectionCloser);
    }

    @Test
    @DisplayName("전송 중 다른 스레드에서 받은 요청은 현재 전송 완료 후 갱신된 위치부터 처리한다.")
    void defersDeliveryRequestedByAnotherThreadUntilCurrentDeliveryCompletes() throws InterruptedException, ExecutionException, TimeoutException {
        SseHistoryPosition upperBound = createHistoryPosition();
        SseHistoryPosition nextPosition = createHistoryPosition("1000-1");

        CountDownLatch deliveryStarted = new CountDownLatch(1);
        CountDownLatch allowDeliveryCompletion = new CountDownLatch(1);

        when(recoveryService.recover(userId, emitter, lastEventId))
                .thenReturn(upperBound);
        when(recoveryService.recoverAfter(userId, connection, emitter, upperBound))
                .thenAnswer(invocation -> {
                    deliveryStarted.countDown();
                    assertThat(allowDeliveryCompletion.await(10, TimeUnit.SECONDS))
                            .as("첫 번째 전송의 완료가 허용되어야 한다.")
                            .isTrue();
                    return nextPosition;
                });

        when(recoveryService.recoverAfter(userId, connection, emitter, nextPosition))
                .thenReturn(nextPosition);

        coordinator.register(userId, connection, emitter);
        coordinator.recover(userId, connection, lastEventId);

        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<?> firstRequest = executor.submit(() ->
                    coordinator.requestDelivery(userId, connection));
            assertThat(deliveryStarted.await(5,TimeUnit.SECONDS))
                    .as("첫 번째 전송이 시작되어야 한다.")
                    .isTrue();

            Future<?> secondRequest = executor.submit(() ->
                    coordinator.requestDelivery(userId, connection));
            secondRequest.get(5, TimeUnit.SECONDS);

            verify(recoveryService, times(1))
                    .recoverAfter(any(), any(), any(), any());

            allowDeliveryCompletion.countDown();;
            firstRequest.get(5, TimeUnit.SECONDS);

            ArgumentCaptor<SseHistoryPosition> positionCaptor = ArgumentCaptor.forClass(SseHistoryPosition.class);
            verify(recoveryService, times(2))
                    .recoverAfter(eq(userId), eq(connection), same(emitter), positionCaptor.capture());
            assertThat(positionCaptor.getAllValues())
                    .containsExactly(upperBound, nextPosition);
        } finally {
            allowDeliveryCompletion.countDown();
            executor.shutdownNow();
            assertThat(executor.awaitTermination(5, TimeUnit.SECONDS))
                    .as("테스트 작업 스레드가 종료되어야 한다.")
                    .isTrue();
        }
    }

    @Test
    @DisplayName("초기 복구 중 다른 스레드에서 받은 요청은 복구 완료 후 해당 위치부터 처리한다.")
    void defersDeliveryRequestedByAnotherThreadUntilRecoveryCompletes() throws InterruptedException, ExecutionException, TimeoutException {
        SseHistoryPosition upperBound = createHistoryPosition();

        CountDownLatch recoveryStarted = new CountDownLatch(1);
        CountDownLatch allowRecoveryCompletion = new CountDownLatch(1);

        when(recoveryService.recover(userId, emitter, lastEventId))
                .thenAnswer(invocation -> {
                    recoveryStarted.countDown();
                    assertThat(allowRecoveryCompletion.await(10, TimeUnit.SECONDS))
                            .as("초기 복구의 완료가 허용되어야 한다.")
                            .isTrue();
                    return upperBound;
                });

        when(recoveryService.recoverAfter(userId, connection, emitter, upperBound))
                .thenReturn(upperBound);
        coordinator.register(userId, connection, emitter);

        ExecutorService executor = Executors.newFixedThreadPool(2);

        try {
            Future<?> recoveryRequest = executor.submit(() ->
                    coordinator.recover(userId, connection, lastEventId));
            assertThat(recoveryStarted.await(5, TimeUnit.SECONDS))
                    .as("초기 복구가 시작되어야 한다.")
                    .isTrue();

            Future<?> deliveryRequest = executor.submit(() ->
                    coordinator.requestDelivery(userId, connection));

            deliveryRequest.get(5, TimeUnit.SECONDS);

            verify(recoveryService, never())
                    .recoverAfter(any(), any(), any(), any());

            allowRecoveryCompletion.countDown();
            recoveryRequest.get(5, TimeUnit.SECONDS);

            verify(recoveryService)
                    .recover(eq(userId), same(emitter), eq(lastEventId));
            verify(recoveryService)
                    .recoverAfter(eq(userId), eq(connection), same(emitter), eq(upperBound));
            verifyNoMoreInteractions(recoveryService);
        } finally {
            allowRecoveryCompletion.countDown();
            executor.shutdownNow();
            assertThat(executor.awaitTermination(5, TimeUnit.SECONDS))
                    .as("테스트 작업 스레드가 종료되어야 한다.")
                    .isTrue();
        }
    }

    @Test
    @DisplayName("한 연결의 전송이 지연되어도 같은 사용자의 다른 연결은 독립적으로 전송한다.")
    void deliversToAnotherConnectionOfSameUserWhileFirstDeliveryIsBlocked() throws InterruptedException, ExecutionException, TimeoutException {
        Connection secondConnection = Connection.create();
        SseEmitter secondEmitter = mock(SseEmitter.class);
        UUID secondLastEventId = UUID.randomUUID();

        SseHistoryPosition firstPosition = createHistoryPosition();
        SseHistoryPosition secondPosition = createHistoryPosition("2000-0");

        CountDownLatch firstDeliveryStarted = new CountDownLatch(1);
        CountDownLatch allowFirstDeliveryCompletion = new CountDownLatch(1);

        when(recoveryService.recover(userId, emitter, lastEventId))
                .thenReturn(firstPosition);
        when(recoveryService.recover(userId, secondEmitter, secondLastEventId))
                .thenReturn(secondPosition);
        when(recoveryService.recoverAfter(userId, connection, emitter, firstPosition))
                .thenAnswer(invocation -> {
                    firstDeliveryStarted.countDown();
                    assertThat(allowFirstDeliveryCompletion.await(10, TimeUnit.SECONDS))
                            .as("첫 번째 연결이 전송완료가 허용되어야 한다.")
                            .isTrue();
                    return firstPosition;
                });

        when(recoveryService.recoverAfter(userId, secondConnection, secondEmitter, secondPosition))
                .thenReturn(secondPosition);

        coordinator.register(userId, connection, emitter);
        coordinator.register(userId, secondConnection, secondEmitter);
        coordinator.recover(userId, connection, lastEventId);
        coordinator.recover(userId, secondConnection, secondLastEventId);

        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<?> firstRequest = executor.submit(() ->
                    coordinator.requestDelivery(userId, connection));
            assertThat(firstDeliveryStarted.await(5, TimeUnit.SECONDS))
                    .as("첫 번째 연결의 전송이 시작되어야 한다.")
                    .isTrue();
            Future<?> secondRequest = executor.submit(() ->
                    coordinator.requestDelivery(userId, secondConnection));
            secondRequest.get(5, TimeUnit.SECONDS);

            assertThat(firstRequest.isDone())
                    .as("첫 번째 연결의 전송은 아직 대기 중이어야 한다.")
                    .isFalse();

            verify(recoveryService)
                    .recoverAfter(eq(userId), eq(secondConnection), same(secondEmitter), eq(secondPosition));
            allowFirstDeliveryCompletion.countDown();
            firstRequest.get(5, TimeUnit.SECONDS);

            verify(recoveryService)
                    .recoverAfter(eq(userId), eq(connection), same(emitter), eq(firstPosition));
            verify(recoveryService, times(2))
                    .recoverAfter(any(), any(), any(), any());

        } finally {
            allowFirstDeliveryCompletion.countDown();
            executor.shutdownNow();
            assertThat(executor.awaitTermination(5, TimeUnit.SECONDS))
                    .as("테스트 작업 스레드가 종료되어야 한다.")
                    .isTrue();
        }
    }

    @Test
    @DisplayName("전송 중 다른 스레드에서 연결을 제거하면 보류한 요청의 후속 조회를 중단한다.")
    void stopsPendingDeliveryWhenAnotherThreadRemovesConnection() throws InterruptedException, ExecutionException, TimeoutException {
        SseHistoryPosition upperBound = createHistoryPosition();
        SseHistoryPosition nextPosition = createHistoryPosition("1000-1");

        CountDownLatch deliveryStarted = new CountDownLatch(1);
        CountDownLatch allowDeliveryCompletion = new CountDownLatch(1);

        when(recoveryService.recover(userId, emitter, lastEventId))
                .thenReturn(upperBound);
        when(recoveryService.recoverAfter(userId, connection, emitter, upperBound))
                .thenAnswer(invocation ->{
                    deliveryStarted.countDown();
                    assertThat(allowDeliveryCompletion.await(10, TimeUnit.SECONDS))
                            .as("진행 중인 전송의 완료가 허용되어야 한다.")
                            .isTrue();

                    return nextPosition;
                });

        coordinator.register(userId, connection, emitter);
        coordinator.recover(userId, connection, lastEventId);

        ExecutorService executor = Executors.newFixedThreadPool(2);

        try {
            Future<?> deliveryRequest = executor.submit(() ->
                    coordinator.requestDelivery(userId, connection));
            assertThat(deliveryStarted.await(5, TimeUnit.SECONDS))
                    .as("전송이 시작되어야 한다.")
                    .isTrue();

            Future<?> removeRequest = executor.submit(() -> {
                coordinator.requestDelivery(userId, connection);
                coordinator.cleanupConnection(userId, connection);
            });

            removeRequest.get(5, TimeUnit.SECONDS);
            assertThat(deliveryRequest.isDone())
                    .as("진행 중인 전송은 아직 대기중이어야 한다.")
                    .isFalse();
            allowDeliveryCompletion.countDown();
            deliveryRequest.get(5, TimeUnit.SECONDS);
            coordinator.requestDelivery(userId, connection);
            verify(recoveryService)
                    .recover(eq(userId), same(emitter), eq(lastEventId));
            verify(recoveryService)
                    .recoverAfter(eq(userId), eq(connection), same(emitter), eq(upperBound));
            verifyNoMoreInteractions(recoveryService);
            verify(connectionCloser).cleanUp(userId, connection);
            verifyNoMoreInteractions(connectionCloser);
        } finally {
            allowDeliveryCompletion.countDown();
            executor.shutdownNow();
            assertThat(executor.awaitTermination(5, TimeUnit.SECONDS))
                    .as("테스트 작업 스레드가 종료되어야 한다.")
                    .isTrue();
        }
    }

    @ParameterizedTest(name = "{index}: {0}")
    @MethodSource("historyReadFailures")
    @DisplayName("후속 이력조회가 일시적으로 실패하면 예외를 전달하지 않고 초기 간격 후 재시도를 한번 예약한다.")
    void schedulesRetryWithInitialDelayWhenHistoryReadFails(DataAccessException failure){
        SseHistoryPosition upperBound = createHistoryPosition();
        Instant now = Instant.parse("2026-11-07T00:00:00Z");

        when(retryTaskScheduler.getClock())
                .thenReturn(Clock.fixed(now, ZoneOffset.UTC));

        doReturn(retryFuture)
                .when(retryTaskScheduler)
                .schedule(any(Runnable.class), any(Instant.class));

        when(recoveryService.recover(userId, emitter, lastEventId))
                .thenReturn(upperBound);
        when(recoveryService.recoverAfter(userId, connection, emitter, upperBound))
                .thenThrow(failure);

        coordinator.register(userId, connection, emitter);
        coordinator.recover(userId, connection, lastEventId);

        assertThatCode(() -> coordinator.requestDelivery(userId, connection))
                .doesNotThrowAnyException();
        verify(retryTaskScheduler)
                .schedule(any(Runnable.class), eq(now.plus(retryProperties.initialDelay())));
        verify(recoveryService).recover(userId, emitter, lastEventId);
        verify(recoveryService).recoverAfter(userId, connection, emitter,upperBound);
        verifyNoMoreInteractions(recoveryService);
        verifyNoInteractions(emitter, connectionCloser);
    }

    @Test
    @DisplayName("예약된 재시도는 마지막 위치부터 조회하고 성공 후 다음 요청에는 갱신된 위치를 사용한다.")
    void retriesFromLastPositionAndUsesUpdatedPositionAfterSuccess(){
        SseHistoryPosition upperBound = createHistoryPosition();
        SseHistoryPosition nextPosition = createHistoryPosition("1000-1");
        Instant now = Instant.parse("2026-11-07T00:00:00Z");

        DataAccessResourceFailureException failure = new DataAccessResourceFailureException("테스트용 후속 이력 조회 실패");

        when(retryTaskScheduler.getClock())
                .thenReturn(Clock.fixed(now, ZoneOffset.UTC));

        doReturn(retryFuture)
                .when(retryTaskScheduler)
                .schedule(any(Runnable.class), any(Instant.class));

        when(recoveryService.recover(userId, emitter, lastEventId))
                .thenReturn(upperBound);
        when(recoveryService.recoverAfter(userId, connection, emitter, upperBound))
                .thenThrow(failure)
                .thenReturn(nextPosition);
        when(recoveryService.recoverAfter(userId, connection, emitter, nextPosition))
                .thenReturn(nextPosition);

        coordinator.register(userId, connection, emitter);
        coordinator.recover(userId, connection, lastEventId);

        assertThatCode(() -> coordinator.requestDelivery(userId, connection))
                .doesNotThrowAnyException();

        verify(recoveryService)
                .recoverAfter(userId, connection, emitter, upperBound);
        ArgumentCaptor<Runnable> taskCaptor = ArgumentCaptor.forClass(Runnable.class);
        verify(retryTaskScheduler)
                .schedule(taskCaptor.capture(), any(Instant.class));
        assertThatCode(() -> taskCaptor.getValue().run())
                .doesNotThrowAnyException();
        coordinator.requestDelivery(userId, connection);

        ArgumentCaptor<SseHistoryPosition> positionCaptor = ArgumentCaptor.forClass(SseHistoryPosition.class);

        verify(recoveryService, times(3))
                .recoverAfter(eq(userId), eq(connection), same(emitter), positionCaptor.capture());
        assertThat(positionCaptor.getAllValues())
                .containsExactly(upperBound, upperBound, nextPosition);
        verify(recoveryService)
                .recover(userId, emitter, lastEventId);
        verifyNoMoreInteractions(recoveryService);
    }

    @Test
    @DisplayName("예약된 재시도도 실패하면 다음 재시도 간격을 두 배로 늘린다.")
    void doublesRetryDelayWhenScheduledRetryFails() {
        SseHistoryPosition upperBound = createHistoryPosition();
        Instant now = Instant.parse("2026-11-07T00:00:00Z");
        AtomicReference<Instant> currentTime = new AtomicReference<>(now);
        Clock retryClock = mock(Clock.class);
        ScheduledFuture<?> secondRetryFuture = mock(ScheduledFuture.class);

        DataAccessResourceFailureException failure =
                new DataAccessResourceFailureException("테스트용 후속 이력 조회 실패");

        when(retryTaskScheduler.getClock()).thenReturn(retryClock);
        when(retryClock.instant()).thenAnswer(invocation -> currentTime.get());

        doReturn(retryFuture, secondRetryFuture)
                .when(retryTaskScheduler)
                .schedule(any(Runnable.class), any(Instant.class));

        when(recoveryService.recover(userId, emitter, lastEventId))
                .thenReturn(upperBound);
        when(recoveryService.recoverAfter(userId, connection, emitter, upperBound))
                .thenThrow(failure);

        coordinator.register(userId, connection, emitter);
        coordinator.recover(userId, connection, lastEventId);

        assertThatCode(() -> coordinator.requestDelivery(userId, connection))
                .doesNotThrowAnyException();

        ArgumentCaptor<Runnable> taskCaptor = ArgumentCaptor.forClass(Runnable.class);

        verify(retryTaskScheduler)
                .schedule(taskCaptor.capture(), any(Instant.class));

        currentTime.set(now.plusSeconds(1));

        assertThatCode(() -> taskCaptor.getValue().run())
                .doesNotThrowAnyException();

        ArgumentCaptor<Instant> timeCaptor = ArgumentCaptor.forClass(Instant.class);

        verify(retryTaskScheduler, times(2))
                .schedule(any(Runnable.class), timeCaptor.capture());

        assertThat(timeCaptor.getAllValues())
                .containsExactly(now.plusSeconds(1), now.plusSeconds(3));

        verify(recoveryService)
                .recover(userId, emitter, lastEventId);
        verify(recoveryService, times(2))
                .recoverAfter(userId, connection, emitter, upperBound);
        verifyNoMoreInteractions(recoveryService);
    }

    @Test
    @DisplayName("재시도 횟수가 남아 있으면 대기 간격을 설정한 최대값까지만 늘려 예약한다.")
    void capsRetryDelayAtConfiguredMaximum() {
        SseRetryProperties properties = new SseRetryProperties(
                Duration.ofSeconds(20),
                Duration.ofSeconds(30),
                3
        );
        coordinator = new SseDeliveryCoordinator(
                recoveryService, retryTaskScheduler, properties, connectionCloser, repository
        );

        SseHistoryPosition upperBound = createHistoryPosition();
        Instant now = Instant.parse("2026-11-07T00:00:00Z");
        AtomicReference<Instant> currentTime = new AtomicReference<>(now);
        AtomicReference<Runnable> scheduledTask = new AtomicReference<>();
        Clock retryClock = mock(Clock.class);

        DataAccessResourceFailureException failure =
                new DataAccessResourceFailureException("테스트용 후속 이력 조회 실패");

        when(retryTaskScheduler.getClock()).thenReturn(retryClock);
        when(retryClock.instant()).thenAnswer(invocation -> currentTime.get());

        doAnswer(invocation -> {
            scheduledTask.set(invocation.getArgument(0, Runnable.class));
            return mock(ScheduledFuture.class);
        }).when(retryTaskScheduler)
                .schedule(any(Runnable.class), any(Instant.class));

        when(recoveryService.recover(userId, emitter, lastEventId))
                .thenReturn(upperBound);
        when(recoveryService.recoverAfter(userId, connection, emitter, upperBound))
                .thenThrow(failure);

        coordinator.register(userId, connection, emitter);
        coordinator.recover(userId, connection, lastEventId);

        assertThatCode(() -> coordinator.requestDelivery(userId, connection))
                .doesNotThrowAnyException();

        currentTime.set(now.plusSeconds(20));
        assertThatCode(() -> scheduledTask.get().run())
                .doesNotThrowAnyException();

        currentTime.set(now.plusSeconds(50));
        assertThatCode(() -> scheduledTask.get().run())
                .doesNotThrowAnyException();

        ArgumentCaptor<Instant> timeCaptor = ArgumentCaptor.forClass(Instant.class);

        verify(retryTaskScheduler, times(3))
                .schedule(any(Runnable.class), timeCaptor.capture());

        assertThat(timeCaptor.getAllValues())
                .containsExactly(now.plusSeconds(20), now.plusSeconds(50), now.plusSeconds(80));

        verify(recoveryService)
                .recover(userId, emitter, lastEventId);
        verify(recoveryService, times(3))
                .recoverAfter(userId, connection, emitter, upperBound);
        verifyNoMoreInteractions(recoveryService);
    }

    @Test
    @DisplayName("재시도가 성공하면 추가 예약을 멈추고 다음 실패는 초기 간격으로 다시 예약한다.")
    void resetsRetryDelayAfterSuccessfulRetry() {
        SseHistoryPosition upperBound = createHistoryPosition();
        SseHistoryPosition nextPosition = createHistoryPosition("1000-1");
        Instant now = Instant.parse("2026-11-07T00:00:00Z");
        AtomicReference<Instant> currentTime = new AtomicReference<>(now);
        AtomicReference<Runnable> scheduledTask = new AtomicReference<>();
        Clock retryClock = mock(Clock.class);

        DataAccessResourceFailureException failure =
                new DataAccessResourceFailureException("테스트용 후속 이력 조회 실패");

        when(retryTaskScheduler.getClock()).thenReturn(retryClock);
        when(retryClock.instant()).thenAnswer(invocation -> currentTime.get());

        doAnswer(invocation -> {
            scheduledTask.set(invocation.getArgument(0, Runnable.class));
            return mock(ScheduledFuture.class);
        }).when(retryTaskScheduler)
                .schedule(any(Runnable.class), any(Instant.class));

        when(recoveryService.recover(userId, emitter, lastEventId))
                .thenReturn(upperBound);
        when(recoveryService.recoverAfter(userId, connection, emitter, upperBound))
                .thenThrow(failure)
                .thenThrow(failure)
                .thenReturn(nextPosition);
        when(recoveryService.recoverAfter(userId, connection, emitter, nextPosition))
                .thenThrow(failure);

        coordinator.register(userId, connection, emitter);
        coordinator.recover(userId, connection, lastEventId);

        assertThatCode(() -> coordinator.requestDelivery(userId, connection))
                .doesNotThrowAnyException();

        currentTime.set(now.plusSeconds(1));
        assertThatCode(() -> scheduledTask.get().run())
                .doesNotThrowAnyException();

        currentTime.set(now.plusSeconds(3));
        assertThatCode(() -> scheduledTask.get().run())
                .doesNotThrowAnyException();

        verify(retryTaskScheduler, times(2))
                .schedule(any(Runnable.class), any(Instant.class));

        currentTime.set(now.plusSeconds(10));
        assertThatCode(() -> coordinator.requestDelivery(userId, connection))
                .doesNotThrowAnyException();

        ArgumentCaptor<Instant> timeCaptor = ArgumentCaptor.forClass(Instant.class);

        verify(retryTaskScheduler, times(3))
                .schedule(any(Runnable.class), timeCaptor.capture());

        assertThat(timeCaptor.getAllValues())
                .containsExactly(now.plusSeconds(1), now.plusSeconds(3), now.plusSeconds(11));

        verify(recoveryService)
                .recover(userId, emitter, lastEventId);
        verify(recoveryService, times(3))
                .recoverAfter(userId, connection, emitter, upperBound);
        verify(recoveryService)
                .recoverAfter(userId, connection, emitter, nextPosition);
        verifyNoMoreInteractions(recoveryService);
    }

    @Test
    @DisplayName("재시도 대기 중 새 전송 요청이 들어와도 추가 조회 없이 기존 예약을 유지한다.")
    void keepsExistingRetryWhenDeliveryIsRequestedWhileWaiting() {
        SseHistoryPosition upperBound = createHistoryPosition();
        Instant now = Instant.parse("2026-11-07T00:00:00Z");

        DataAccessResourceFailureException failure =
                new DataAccessResourceFailureException("테스트용 후속 이력 조회 실패");

        when(retryTaskScheduler.getClock())
                .thenReturn(Clock.fixed(now, ZoneOffset.UTC));

        doReturn(retryFuture)
                .when(retryTaskScheduler)
                .schedule(any(Runnable.class), any(Instant.class));

        when(recoveryService.recover(userId, emitter, lastEventId))
                .thenReturn(upperBound);
        when(recoveryService.recoverAfter(userId, connection, emitter, upperBound))
                .thenThrow(failure);

        coordinator.register(userId, connection, emitter);
        coordinator.recover(userId, connection, lastEventId);

        assertThatCode(() -> {
            coordinator.requestDelivery(userId, connection);
            coordinator.requestDelivery(userId, connection);
            coordinator.requestDelivery(userId, connection);
            coordinator.requestDelivery(userId, connection);
        }).doesNotThrowAnyException();

        verify(retryTaskScheduler).schedule(
                any(Runnable.class),
                eq(now.plus(retryProperties.initialDelay()))
        );
        verify(retryFuture, never()).cancel(anyBoolean());

        verify(recoveryService)
                .recover(userId, emitter, lastEventId);
        verify(recoveryService)
                .recoverAfter(userId, connection, emitter, upperBound);
        verifyNoMoreInteractions(recoveryService);
    }

    @Test
    @DisplayName("연결을 제거하면 재시도 예약을 취소하고 뒤늦게 실행된 작업도 추가 조회하지 않는다.")
    void cancelsRetryAndSkipsLateTaskWhenConnectionIsRemoved() {
        SseHistoryPosition upperBound = createHistoryPosition();
        Instant now = Instant.parse("2026-11-07T00:00:00Z");

        DataAccessResourceFailureException failure =
                new DataAccessResourceFailureException("테스트용 후속 이력 조회 실패");

        when(retryTaskScheduler.getClock())
                .thenReturn(Clock.fixed(now, ZoneOffset.UTC));

        doReturn(retryFuture)
                .when(retryTaskScheduler)
                .schedule(any(Runnable.class), any(Instant.class));

        when(recoveryService.recover(userId, emitter, lastEventId))
                .thenReturn(upperBound);
        when(recoveryService.recoverAfter(userId, connection, emitter, upperBound))
                .thenThrow(failure);

        coordinator.register(userId, connection, emitter);
        coordinator.recover(userId, connection, lastEventId);

        assertThatCode(() -> coordinator.requestDelivery(userId, connection))
                .doesNotThrowAnyException();

        ArgumentCaptor<Runnable> taskCaptor = ArgumentCaptor.forClass(Runnable.class);

        verify(retryTaskScheduler)
                .schedule(taskCaptor.capture(), any(Instant.class));

        coordinator.cleanupConnection(userId, connection);

        verify(retryFuture).cancel(false);

        assertThatCode(() -> taskCaptor.getValue().run())
                .doesNotThrowAnyException();

        verify(retryTaskScheduler)
                .schedule(any(Runnable.class), any(Instant.class));
        verify(recoveryService)
                .recover(userId, emitter, lastEventId);
        verify(recoveryService)
                .recoverAfter(userId, connection, emitter, upperBound);
        verifyNoMoreInteractions(recoveryService);
        verify(connectionCloser).cleanUp(userId, connection);
        verifyNoMoreInteractions(connectionCloser);
    }

    @Test
    @DisplayName("같은 사용자의 한 연결이 재시도를 기다려도 다른 연결은 후속 조회하고 기존 예약을 유지한다.")
    void deliversToAnotherConnectionWhileFirstConnectionWaitsForRetry() {
        Connection secondConnection = Connection.create();
        SseEmitter secondEmitter = mock(SseEmitter.class);
        UUID secondLastEventId = UUID.randomUUID();

        SseHistoryPosition firstPosition = createHistoryPosition();
        SseHistoryPosition secondPosition = createHistoryPosition("2000-0");
        Instant now = Instant.parse("2026-11-07T00:00:00Z");

        DataAccessResourceFailureException failure =
                new DataAccessResourceFailureException("테스트용 후속 이력 조회 실패");

        when(retryTaskScheduler.getClock())
                .thenReturn(Clock.fixed(now, ZoneOffset.UTC));

        doReturn(retryFuture)
                .when(retryTaskScheduler)
                .schedule(any(Runnable.class), any(Instant.class));

        when(recoveryService.recover(userId, emitter, lastEventId))
                .thenReturn(firstPosition);
        when(recoveryService.recover(
                userId, secondEmitter, secondLastEventId
        )).thenReturn(secondPosition);

        when(recoveryService.recoverAfter(
                userId, connection, emitter, firstPosition
        )).thenThrow(failure);
        when(recoveryService.recoverAfter(
                userId, secondConnection, secondEmitter, secondPosition
        )).thenReturn(secondPosition);

        coordinator.register(userId, connection, emitter);
        coordinator.register(userId, secondConnection, secondEmitter);

        coordinator.recover(userId, connection, lastEventId);
        coordinator.recover(userId, secondConnection, secondLastEventId);

        assertThatCode(() -> coordinator.requestDelivery(userId, connection))
                .doesNotThrowAnyException();
        assertThatCode(() -> coordinator.requestDelivery(userId, secondConnection))
                .doesNotThrowAnyException();

        verify(recoveryService)
                .recover(userId, emitter, lastEventId);
        verify(recoveryService)
                .recover(userId, secondEmitter, secondLastEventId);
        verify(recoveryService)
                .recoverAfter(userId, connection, emitter, firstPosition);
        verify(recoveryService)
                .recoverAfter(userId, secondConnection, secondEmitter, secondPosition);
        verifyNoMoreInteractions(recoveryService);

        verify(retryTaskScheduler).schedule(
                any(Runnable.class),
                eq(now.plus(retryProperties.initialDelay()))
        );
        verify(retryFuture, never()).cancel(anyBoolean());
    }

    @ParameterizedTest(name = "최대 재시도 횟수={0}")
    @ValueSource(ints = {1, 3})
    @DisplayName("최초 조회 이후 설정 횟수만큼 재시도하고 모두 실패하면 연결을 종료하며 이후 요청과 늦은 작업을 무시한다.")
    void closesConnectionAfterConfiguredRetriesAndIgnoresLaterRequests(int maxRetries) {
        SseRetryProperties properties = new SseRetryProperties(
                Duration.ofSeconds(1), Duration.ofSeconds(30), maxRetries
        );
        coordinator = new SseDeliveryCoordinator(recoveryService, retryTaskScheduler, properties, connectionCloser, repository);

        SseHistoryPosition upperBound = createHistoryPosition();
        AtomicReference<Instant> currentTime = new AtomicReference<>(
                Instant.parse("2026-11-07T00:00:00Z")
        );
        Clock retryClock = mock(Clock.class);
        List<Runnable> scheduledTasks = new ArrayList<>();
        List<Instant> scheduledTimes = new ArrayList<>();
        DataAccessResourceFailureException failure =
                new DataAccessResourceFailureException("테스트용 후속 이력 조회 실패");

        when(retryTaskScheduler.getClock()).thenReturn(retryClock);
        when(retryClock.instant()).thenAnswer(invocation -> currentTime.get());
        doAnswer(invocation -> {
            scheduledTasks.add(invocation.getArgument(0, Runnable.class));
            scheduledTimes.add(invocation.getArgument(1, Instant.class));
            return mock(ScheduledFuture.class);
        }).when(retryTaskScheduler).schedule(any(Runnable.class), any(Instant.class));

        when(recoveryService.recover(userId, emitter, lastEventId))
                .thenReturn(upperBound);
        when(recoveryService.recoverAfter(userId, connection, emitter, upperBound))
                .thenThrow(failure);

        coordinator.register(userId, connection, emitter);
        coordinator.recover(userId, connection, lastEventId);

        assertThatCode(() -> coordinator.requestDelivery(userId, connection))
                .doesNotThrowAnyException();

        for (int retryIndex = 0; retryIndex < maxRetries; retryIndex++) {
            assertThat(scheduledTasks).hasSize(retryIndex + 1);
            verify(connectionCloser, never()).close(userId, connection, emitter);

            assertThatCode(() -> coordinator.requestDelivery(userId, connection))
                    .doesNotThrowAnyException();
            verify(recoveryService, times(retryIndex + 1))
                    .recoverAfter(userId, connection, emitter, upperBound);

            currentTime.set(scheduledTimes.get(retryIndex));
            Runnable task = scheduledTasks.get(retryIndex);
            assertThatCode(task::run).doesNotThrowAnyException();
        }

        verify(connectionCloser).close(userId, connection, emitter);
        assertThat(scheduledTasks).hasSize(maxRetries);

        assertThatCode(() -> coordinator.requestDelivery(userId, connection))
                .doesNotThrowAnyException();
        for (Runnable task : List.copyOf(scheduledTasks)) {
            assertThatCode(task::run).doesNotThrowAnyException();
        }

        verify(recoveryService).recover(userId, emitter, lastEventId);
        verify(recoveryService, times(maxRetries + 1))
                .recoverAfter(userId, connection, emitter, upperBound);
        verifyNoMoreInteractions(recoveryService);
        verify(retryTaskScheduler, times(maxRetries))
                .schedule(any(Runnable.class), any(Instant.class));
        verify(connectionCloser, times(1)).close(userId, connection, emitter);
        verifyNoMoreInteractions(connectionCloser);
    }

    @Test
    @DisplayName("마지막 허용 재시도가 성공하면 연결을 유지하고 다음 실패에는 재시도 횟수를 새로 부여한다.")
    void resetsRetryBudgetAfterLastAllowedRetrySucceeds() {
        SseRetryProperties properties = new SseRetryProperties(
                Duration.ofSeconds(1), Duration.ofSeconds(30), 1
        );
        coordinator = new SseDeliveryCoordinator(recoveryService, retryTaskScheduler, properties, connectionCloser, repository);

        SseHistoryPosition upperBound = createHistoryPosition();
        SseHistoryPosition nextPosition = createHistoryPosition("1000-1");
        SseHistoryPosition finalPosition = createHistoryPosition("1000-2");
        Instant now = Instant.parse("2026-11-07T00:00:00Z");
        AtomicReference<Instant> currentTime = new AtomicReference<>(now);
        Clock retryClock = mock(Clock.class);
        List<Runnable> scheduledTasks = new ArrayList<>();
        DataAccessResourceFailureException failure =
                new DataAccessResourceFailureException("테스트용 후속 이력 조회 실패");

        when(retryTaskScheduler.getClock()).thenReturn(retryClock);
        when(retryClock.instant()).thenAnswer(invocation -> currentTime.get());
        doAnswer(invocation -> {
            scheduledTasks.add(invocation.getArgument(0, Runnable.class));
            return mock(ScheduledFuture.class);
        }).when(retryTaskScheduler).schedule(any(Runnable.class), any(Instant.class));

        when(recoveryService.recover(userId, emitter, lastEventId))
                .thenReturn(upperBound);
        when(recoveryService.recoverAfter(userId, connection, emitter, upperBound))
                .thenThrow(failure)
                .thenReturn(nextPosition);
        when(recoveryService.recoverAfter(userId, connection, emitter, nextPosition))
                .thenThrow(failure)
                .thenReturn(finalPosition);

        coordinator.register(userId, connection, emitter);
        coordinator.recover(userId, connection, lastEventId);

        assertThatCode(() -> coordinator.requestDelivery(userId, connection))
                .doesNotThrowAnyException();
        assertThat(scheduledTasks).hasSize(1);

        currentTime.set(now.plusSeconds(1));
        assertThatCode(scheduledTasks.get(0)::run).doesNotThrowAnyException();
        assertThat(scheduledTasks).hasSize(1);
        verifyNoInteractions(connectionCloser);

        currentTime.set(now.plusSeconds(10));
        assertThatCode(() -> coordinator.requestDelivery(userId, connection))
                .doesNotThrowAnyException();
        assertThat(scheduledTasks).hasSize(2);

        currentTime.set(now.plusSeconds(11));
        assertThatCode(scheduledTasks.get(1)::run).doesNotThrowAnyException();

        ArgumentCaptor<Instant> timeCaptor = ArgumentCaptor.forClass(Instant.class);
        verify(retryTaskScheduler, times(2))
                .schedule(any(Runnable.class), timeCaptor.capture());
        assertThat(timeCaptor.getAllValues()).containsExactly(now.plusSeconds(1), now.plusSeconds(11));

        verify(recoveryService).recover(userId, emitter, lastEventId);
        verify(recoveryService, times(2))
                .recoverAfter(userId, connection, emitter, upperBound);
        verify(recoveryService, times(2))
                .recoverAfter(userId, connection, emitter, nextPosition);
        verifyNoMoreInteractions(recoveryService);
        verifyNoInteractions(connectionCloser);
    }

    @Test
    @DisplayName("한 연결이 재시도를 소진해 종료되어도 같은 사용자의 다른 연결은 예약된 재시도와 후속 전송을 계속한다.")
    void keepsOtherConnectionRetryAndDeliveryAfterFirstConnectionExhaustsRetries() {
        SseRetryProperties properties = new SseRetryProperties(
                Duration.ofSeconds(1), Duration.ofSeconds(30), 1
        );
        coordinator = new SseDeliveryCoordinator(recoveryService, retryTaskScheduler, properties, connectionCloser, repository);

        Connection secondConnection = Connection.create();
        SseEmitter secondEmitter = mock(SseEmitter.class);
        UUID secondLastEventId = UUID.randomUUID();
        SseHistoryPosition firstPosition = createHistoryPosition();
        SseHistoryPosition secondPosition = createHistoryPosition("2000-0");
        SseHistoryPosition nextPosition = createHistoryPosition("2000-1");
        Instant now = Instant.parse("2026-11-07T00:00:00Z");
        ScheduledFuture<?> secondRetryFuture = mock(ScheduledFuture.class);
        DataAccessResourceFailureException failure =
                new DataAccessResourceFailureException("테스트용 후속 이력 조회 실패");

        when(retryTaskScheduler.getClock()).thenReturn(Clock.fixed(now, ZoneOffset.UTC));
        doReturn(retryFuture, secondRetryFuture)
                .when(retryTaskScheduler).schedule(any(Runnable.class), any(Instant.class));
        when(recoveryService.recover(userId, emitter, lastEventId))
                .thenReturn(firstPosition);
        when(recoveryService.recover(userId, secondEmitter, secondLastEventId))
                .thenReturn(secondPosition);
        when(recoveryService.recoverAfter(userId, connection, emitter, firstPosition))
                .thenThrow(failure);
        when(recoveryService.recoverAfter(userId, secondConnection, secondEmitter, secondPosition))
                .thenThrow(failure)
                .thenReturn(nextPosition);
        when(recoveryService.recoverAfter(userId, secondConnection, secondEmitter, nextPosition))
                .thenReturn(nextPosition);

        coordinator.register(userId, connection, emitter);
        coordinator.register(userId, secondConnection, secondEmitter);
        coordinator.recover(userId, connection, lastEventId);
        coordinator.recover(userId, secondConnection, secondLastEventId);

        assertThatCode(() -> coordinator.requestDelivery(userId, connection))
                .doesNotThrowAnyException();
        assertThatCode(() -> coordinator.requestDelivery(userId, secondConnection))
                .doesNotThrowAnyException();

        ArgumentCaptor<Runnable> taskCaptor = ArgumentCaptor.forClass(Runnable.class);
        verify(retryTaskScheduler, times(2))
                .schedule(taskCaptor.capture(), any(Instant.class));

        assertThatCode(taskCaptor.getAllValues().get(0)::run).doesNotThrowAnyException();
        verify(connectionCloser).close(userId, connection, emitter);
        verify(secondRetryFuture, never()).cancel(anyBoolean());

        assertThatCode(taskCaptor.getAllValues().get(1)::run).doesNotThrowAnyException();
        assertThatCode(() -> coordinator.requestDelivery(userId, secondConnection))
                .doesNotThrowAnyException();

        verify(recoveryService).recover(userId, emitter, lastEventId);
        verify(recoveryService).recover(userId, secondEmitter, secondLastEventId);
        verify(recoveryService, times(2))
                .recoverAfter(userId, connection, emitter, firstPosition);
        verify(recoveryService, times(2))
                .recoverAfter(userId, secondConnection, secondEmitter, secondPosition);
        verify(recoveryService).recoverAfter(userId, secondConnection, secondEmitter, nextPosition);
        verifyNoMoreInteractions(recoveryService);
        verify(retryTaskScheduler, times(2)).schedule(any(Runnable.class), any(Instant.class));
        verify(connectionCloser, never()).close(userId, secondConnection, secondEmitter);
        verifyNoMoreInteractions(connectionCloser);
    }

    @Test
    @DisplayName("연결 종료를 요청하면 종료 처리를 위임하고 이후 복구와 전송을 수행하지 않는다.")
    void closeConnectionDelegatesClosureAndPreventsFurtherDelivery(){
        coordinator.register(userId, connection, emitter);

        coordinator.closeConnection(userId, connection);

        coordinator.recover(userId, connection, lastEventId);
        coordinator.requestDelivery(userId, connection);

        verify(connectionCloser).close(userId, connection, emitter);
        verifyNoMoreInteractions(recoveryService, retryTaskScheduler);
    }

    @Test
    @DisplayName("중복 상태 등록을 거부하고 기존 연결의 복구 상태를 유지한다.")
    void rejectsDuplicateRegistrationAndPreservesExistingState(){
        SseEmitter replacementEmitter = mock(SseEmitter.class);
        SseHistoryPosition position = createHistoryPosition();
        when(recoveryService.recover(userId, emitter, lastEventId)).thenReturn(position);

        coordinator.register(userId, connection, emitter);
        coordinator.recover(userId, connection, lastEventId);
        assertThatThrownBy(() -> coordinator.register(userId, connection, replacementEmitter))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("이미 등록된 SSE 연결입니다.");

        coordinator.requestDelivery(userId, connection);
        verify(recoveryService).recover(userId, emitter, lastEventId);
        verify(recoveryService).recoverAfter(userId, connection, emitter, position);
        verifyNoMoreInteractions(recoveryService);
    }

    @Test
    @DisplayName("연결을 정리하면 HTTP 종료 요청 없이 저장소 정리를 위임하고 이후 복구와 전송을 수행하지 않는다.")
    void cleanupConnectionPreventsFurtherDeliveryWithoutClosingEmitter(){
        coordinator.register(userId, connection, emitter);
        coordinator.cleanupConnection(userId, connection);

        coordinator.recover(userId, connection, lastEventId);
        coordinator.requestDelivery(userId, connection);

        verify(connectionCloser).cleanUp(userId, connection);
        verifyNoMoreInteractions(connectionCloser);
        verifyNoInteractions(recoveryService, retryTaskScheduler, emitter);
    }


    @Test
    @DisplayName("조정기 등록은 저장소에 emitter를 공개하고 저장소의 객체로 초기 복구한다.")
    void registrationPublishesEmitterAndRecoveryUsesRepository(){
        coordinator.register(userId, connection, emitter);
        assertThat(repository.findByUserIdAndConnection(userId, connection)).containsSame(emitter);
        coordinator.recover(userId, connection, lastEventId);
        verify(recoveryService).recover(userId, emitter, lastEventId);
    }

    @Test
    @DisplayName("초기 복구 전에 저장소 연결이 사라지면 이력 조회를 수행하지 않는다.")
    void missingEmitterPreventsInitialRecovery(){
        coordinator.register(userId, connection, emitter);
        repository.delete(userId, connection);
        coordinator.recover(userId, connection, lastEventId);
        coordinator.requestDelivery(userId, connection);
        verifyNoInteractions(recoveryService, retryTaskScheduler, connectionCloser);
    }

    @Test
    @DisplayName("후속 전송 전에 저장소 연결이 사라지면 이력 조회를 수행하지 않는다.")
    void missingEmitterPreventsSubsequentDelivery(){
        coordinator.register(userId, connection, emitter);
        coordinator.recover(userId, connection, lastEventId);
        repository.delete(userId, connection);
        coordinator.requestDelivery(userId, connection);
        verify(recoveryService).recover(userId, emitter, lastEventId);
        verifyNoMoreInteractions(recoveryService);
        verifyNoInteractions(retryTaskScheduler, connectionCloser);
    }

    @Test
    @DisplayName("저장소 등록 실패 시 새 상태를 취소하고 기존 저장소 연결은 유지한다.")
    void failedRepositoryRegistrationRemovesOnlyNewState(){
        SseEmitter existingEmitter = mock(SseEmitter.class);
        repository.save(userId, connection, existingEmitter);
        assertThatThrownBy(() -> coordinator.register(userId, connection, emitter))
                .isInstanceOf(IllegalStateException.class);
        coordinator.recover(userId, connection, lastEventId);
        coordinator.requestDelivery(userId, connection);
        assertThat(repository.findByUserIdAndConnection(userId, connection)).containsSame(existingEmitter);
        verifyNoInteractions(recoveryService, retryTaskScheduler, connectionCloser);
    }

    @ParameterizedTest(name = "진입 경로={0}")
    @ValueSource(strings = {"delivery", "retry", "cleanup", "close"})
    @DisplayName("저장소 연결이 사라지면 남은 상태와 예약을 정리하고 늦은 작업은 전송하지 않는다.")
    void missingEmitterStopsPendingRetry(String entryPoint){
        SseHistoryPosition position = createHistoryPosition();
        when(recoveryService.recover(userId, emitter, lastEventId)).thenReturn(position);
        when(recoveryService.recoverAfter(userId, connection, emitter, position))
                .thenThrow(new DataAccessResourceFailureException("테스트용 조회 실패"));
        when(retryTaskScheduler.getClock()).thenReturn(Clock.systemUTC());
        doReturn(retryFuture).when(retryTaskScheduler).schedule(any(Runnable.class), any(Instant.class));
        coordinator.register(userId, connection, emitter);
        coordinator.recover(userId, connection, lastEventId);
        coordinator.requestDelivery(userId, connection);
        ArgumentCaptor<Runnable> taskCaptor = ArgumentCaptor.forClass(Runnable.class);
        verify(retryTaskScheduler).schedule(taskCaptor.capture(), any(Instant.class));
        repository.delete(userId, connection);

        switch (entryPoint) {
            case "delivery" -> coordinator.requestDelivery(userId, connection);
            case "retry" -> taskCaptor.getValue().run();
            case "cleanup" -> coordinator.cleanupConnection(userId, connection);
            case "close" -> coordinator.closeConnection(userId, connection);
            default -> throw new AssertionError(entryPoint);
        }
        if (!entryPoint.equals("retry")) verify(retryFuture).cancel(false);
        taskCaptor.getValue().run();
        coordinator.requestDelivery(userId, connection);
        verify(recoveryService).recover(userId, emitter, lastEventId);
        verify(recoveryService).recoverAfter(userId, connection, emitter, position);
        verifyNoMoreInteractions(recoveryService);
        verify(retryTaskScheduler, times(1)).schedule(any(Runnable.class), any(Instant.class));
        verifyNoInteractions(connectionCloser);
    }

    @Test
    @DisplayName("등록 중 복구 요청은 저장 완료를 기다리고 등록 전 부재를 연결 종료로 판단하지 않는다.")
    void recoveryWaitsUntilRepositoryRegistrationCompletes() throws Exception {
        SseEmitterRepository slowRepository = spy(new InMemorySseEmitterRepository());
        SseDeliveryCoordinator registeringCoordinator = new SseDeliveryCoordinator(
                recoveryService, retryTaskScheduler, retryProperties, connectionCloser, slowRepository);
        CountDownLatch registrationStarted = new CountDownLatch(1);
        CountDownLatch allowRegistration = new CountDownLatch(1);
        CountDownLatch recoveryRequested = new CountDownLatch(1);
        doAnswer(invocation -> {
            registrationStarted.countDown();
            assertThat(allowRegistration.await(5, TimeUnit.SECONDS)).isTrue();
            return invocation.callRealMethod();
        }).when(slowRepository).save(userId, connection, emitter);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<?> registration = executor.submit(() -> registeringCoordinator.register(userId, connection, emitter));
            assertThat(registrationStarted.await(5, TimeUnit.SECONDS)).isTrue();
            Future<?> recovery = executor.submit(() -> {
                recoveryRequested.countDown();
                registeringCoordinator.recover(userId, connection, lastEventId);
            });
            assertThat(recoveryRequested.await(5, TimeUnit.SECONDS)).isTrue();
            assertThatThrownBy(() -> recovery.get(100, TimeUnit.MILLISECONDS)).isInstanceOf(TimeoutException.class);
            verifyNoInteractions(recoveryService);
            allowRegistration.countDown();
            registration.get(5, TimeUnit.SECONDS);
            recovery.get(5, TimeUnit.SECONDS);
            assertThat(slowRepository.findByUserIdAndConnection(userId, connection)).containsSame(emitter);
            verify(recoveryService).recover(userId, emitter, lastEventId);
        } finally {
            allowRegistration.countDown();
            executor.shutdownNow();
            assertThat(executor.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
        }
    }

    @Test
    @DisplayName("조정기 상태가 이미 없어도 저장소에 남은 일치하는 연결의 정리를 위임한다.")
    void cleanupWithoutStateStillDelegatesRepositoryCleanup(){
        repository.save(userId, connection, emitter);
        coordinator.cleanupConnection(userId, connection);
        verify(connectionCloser).cleanUp(userId, connection);
        verifyNoInteractions(recoveryService, retryTaskScheduler);
    }

    private SseHistoryPosition createHistoryPosition(String position) {
        return new SseHistoryPosition(position);
    }

    private SseHistoryPosition createHistoryPosition() {
        return createHistoryPosition("1000-0");
    }

    private static Stream<RuntimeException> nonRetryableFailures() {
        return Stream.of(
                new UncheckedIOException("테스트용 SSE 이벤트 전송 실패", new IOException("테스트용 연결 오류")),
                new IllegalStateException("테스트용 SSE 연결 상태 오류"),
                new IllegalArgumentException("테스트용 SSE 이벤트 데이터 오류")
        );
    }

    private static Stream<DataAccessException> historyReadFailures(){
        return Stream.of(
                new DataAccessResourceFailureException("테스트용 Redis 연결 실패"),
                new QueryTimeoutException("테스트용 Redis 조회 시간 초과")
        );
    }

}
