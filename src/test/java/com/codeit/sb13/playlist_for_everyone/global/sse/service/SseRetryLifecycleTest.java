package com.codeit.sb13.playlist_for_everyone.global.sse.service;

import com.codeit.sb13.playlist_for_everyone.global.sse.config.SseProperties;
import com.codeit.sb13.playlist_for_everyone.global.sse.config.SseRetryProperties;
import com.codeit.sb13.playlist_for_everyone.global.sse.event.Connection;
import com.codeit.sb13.playlist_for_everyone.global.sse.history.SseHistoryPosition;
import com.codeit.sb13.playlist_for_everyone.global.sse.repository.SseEmitterRepository;
import com.codeit.sb13.playlist_for_everyone.global.sse.repository.impl.InMemorySseEmitterRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockedConstruction;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ScheduledFuture;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * 실제 연결 서비스·coordinator·메모리 저장소에 조회 서비스와 예약 실행기 Mock을 연결한다.
 * 콜백과 예약 Runnable을 직접 실행하여 정리 이후 늦은 작업이 다시 조회하지 않는지 검증한다.
 * 마지막 테스트는 실제 emitter의 동일성과 타임아웃 설정을 확인하며, HTTP의 실제 만료 시각을 재지는 않는다.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("SSE 연결 종료와 재시도 예약 정리")
public class SseRetryLifecycleTest {

    @Mock
    private SseRecoveryService recoveryService;

    @Mock
    private TaskScheduler retryTaskScheduler;

    @Mock
    private ScheduledFuture<?> retryFuture;

    private SseEmitterRepository repository;
    private SseDeliveryCoordinator coordinator;
    private SseConnectionService connectionService;

    @BeforeEach
    void setUp(){
        repository = new InMemorySseEmitterRepository();
        SseRetryProperties retryProperties = new SseRetryProperties(
                Duration.ofSeconds(1),
                Duration.ofSeconds(30),
                3);
        coordinator = new SseDeliveryCoordinator(
                recoveryService,
                retryTaskScheduler,
                retryProperties,
                new SseConnectionCloser(repository), repository);

        SseProperties properties = new SseProperties(
                Duration.ofSeconds(15),
                Duration.ofMinutes(30));

        connectionService = new SseConnectionService(
                repository,
                properties,
                coordinator);
    }

    @Test
    @DisplayName("연결 완료 콜백이 실행되면 재시도 예약을 취소하고 이후 후속 조회를 차단한다.")
    void completionCallbackCancelsRetryAndPreventsFurtherDelivery(){
        UUID userId = UUID.randomUUID();
        UUID lastEventId = UUID.randomUUID();
        SseHistoryPosition upperBound = new SseHistoryPosition("1000-0");

        stubRecoveryAndRetrySchedule(userId, lastEventId, upperBound);

        try (MockedConstruction<SseEmitter> ignored = mockConstruction(SseEmitter.class)){
            SseEmitter emitter = connectionService.connect(userId, lastEventId);

            Map<Connection, SseEmitter> connections = repository.findAllByUserId(userId);
            assertThat(connections).hasSize(1);

            Connection connection = connections.keySet().iterator().next();
            Runnable retryTask = scheduleRetryAfterReadFailure(userId, connection, emitter, upperBound);
            ArgumentCaptor<Runnable> completionCaptor = ArgumentCaptor.forClass(Runnable.class);

            verify(emitter).onCompletion(completionCaptor.capture());
            completionCaptor.getValue().run();

            verify(retryFuture).cancel(false);
            assertThat(repository.findAllByUserId(userId)).isEmpty();
            assertThatCode(retryTask::run)
                    .doesNotThrowAnyException();
            assertThatCode(() -> coordinator.requestDelivery(userId, connection))
                    .doesNotThrowAnyException();

            assertThat(repository.findAll())
                    .doesNotContainKey(userId);

            verify(recoveryService)
                    .recover(userId, emitter, lastEventId);
            verify(recoveryService).recoverAfter(userId, connection, emitter, upperBound);
            verifyNoMoreInteractions(recoveryService, retryFuture);
            verify(retryTaskScheduler).schedule(any(Runnable.class), any(Instant.class));

        }
    }

    @Test
    @DisplayName("컨텍스트가 종료되면 재시도 예약과 연결을 정리하고 이후 후속 조회를 차단한다.")
    void contextCloseCancelsRetryAndPreventsFurtherDelivery(){
        UUID userId = UUID.randomUUID();
        UUID lastEventId = UUID.randomUUID();
        SseHistoryPosition upperBound = new SseHistoryPosition("1000-0");

        stubRecoveryAndRetrySchedule(userId, lastEventId, upperBound);

        try (MockedConstruction<SseEmitter> ignored = mockConstruction(SseEmitter.class)){
            SseEmitter emitter = connectionService.connect(userId, lastEventId);

            Map<Connection, SseEmitter> connections = repository.findAllByUserId(userId);
            assertThat(connections).hasSize(1);

            Connection connection = connections.keySet().iterator().next();
            Runnable retryTask = scheduleRetryAfterReadFailure(userId, connection, emitter, upperBound);

            try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()){
                context.registerBean(SseConnectionService.class, () -> connectionService);
                context.refresh();

                verify(emitter, never()).complete();
                verify(retryFuture, never()).cancel(anyBoolean());
            }

            verify(retryFuture).cancel(false);
            assertThat(repository.findAllByUserId(userId)).isEmpty();
            assertThatCode(retryTask::run)
                    .doesNotThrowAnyException();
            assertThatCode(() -> coordinator.requestDelivery(userId, connection))
                    .doesNotThrowAnyException();

            assertThat(repository.findAll())
                    .doesNotContainKey(userId);

            verify(recoveryService)
                    .recover(userId, emitter, lastEventId);
            verify(recoveryService).recoverAfter(userId, connection, emitter, upperBound);
            verifyNoMoreInteractions(recoveryService, retryFuture);
            verify(retryTaskScheduler).schedule(any(Runnable.class), any(Instant.class));

            verify(emitter).complete();
            verify(emitter, never()).completeWithError(any());
        }
    }

    @Test
    @DisplayName("오류 콜백이 실행되면 재시도 예약을 취소하고 이후 후속 조히를 차단한다.")
    void errorCallbackCancelRetryAndPreventsFurtherDelivery(){
        UUID userId = UUID.randomUUID();
        UUID lastEventId = UUID.randomUUID();
        SseHistoryPosition upperBound = new SseHistoryPosition("1000-0");

        stubRecoveryAndRetrySchedule(userId, lastEventId, upperBound);

        try (MockedConstruction<SseEmitter> ignored = mockConstruction(SseEmitter.class)){
            SseEmitter emitter = connectionService.connect(userId, lastEventId);

            Map<Connection, SseEmitter> connections = repository.findAllByUserId(userId);
            assertThat(connections).hasSize(1);

            Connection connection = connections.keySet().iterator().next();
            Runnable retryTask = scheduleRetryAfterReadFailure(userId, connection, emitter, upperBound);

            ArgumentCaptor<Consumer<Throwable>> errorCaptor = ArgumentCaptor.captor();

            verify(emitter).onError(errorCaptor.capture());
            errorCaptor.getValue().accept(new IOException("테스트용 SSE 연결 오류"));

            verify(retryFuture).cancel(false);
            assertThat(repository.findAllByUserId(userId)).isEmpty();
            assertThatCode(retryTask::run)
                    .doesNotThrowAnyException();
            assertThatCode(() -> coordinator.requestDelivery(userId, connection))
                    .doesNotThrowAnyException();

            assertThat(repository.findAll())
                    .doesNotContainKey(userId);

            verify(recoveryService)
                    .recover(userId, emitter, lastEventId);
            verify(recoveryService).recoverAfter(userId, connection, emitter, upperBound);
            verifyNoMoreInteractions(recoveryService, retryFuture);
            verify(retryTaskScheduler).schedule(any(Runnable.class), any(Instant.class));

            verify(emitter, never()).complete();
            verify(emitter, never()).completeWithError(any());

        }
    }

    @Test
    @DisplayName("타임아웃 후 완료 콜백이 반복되어도 재시도를 한 번만 취소하고 이후 조회를 차단한다.")
    void timeoutThenRepeatedCompletionCancelsRetryOnceAndPreventsFurtherDelivery(){
        UUID userId = UUID.randomUUID();
        UUID lastEventId = UUID.randomUUID();
        SseHistoryPosition upperBound = new SseHistoryPosition("1000-0");

        stubRecoveryAndRetrySchedule(userId, lastEventId, upperBound);

        try (MockedConstruction<SseEmitter> ignored = mockConstruction(SseEmitter.class)){
            SseEmitter emitter = connectionService.connect(userId, lastEventId);

            Map<Connection, SseEmitter> connections = repository.findAllByUserId(userId);
            assertThat(connections).hasSize(1);

            Connection connection = connections.keySet().iterator().next();
            Runnable retryTask = scheduleRetryAfterReadFailure(userId, connection, emitter, upperBound);

            ArgumentCaptor<Runnable> timeoutCaptor = ArgumentCaptor.forClass(Runnable.class);
            ArgumentCaptor<Runnable> completionCaptor = ArgumentCaptor.forClass(Runnable.class);

            verify(emitter).onTimeout(timeoutCaptor.capture());
            verify(emitter).onCompletion(completionCaptor.capture());
            verify(emitter, never()).complete();
            timeoutCaptor.getValue().run();

            assertThatCode(()->{
                completionCaptor.getValue().run();
                completionCaptor.getValue().run();
            }).doesNotThrowAnyException();

            verify(retryFuture).cancel(false);
            assertThat(repository.findAllByUserId(userId)).isEmpty();
            assertThatCode(retryTask::run)
                    .doesNotThrowAnyException();
            assertThatCode(() -> coordinator.requestDelivery(userId, connection))
                    .doesNotThrowAnyException();

            assertThat(repository.findAll())
                    .doesNotContainKey(userId);

            verify(recoveryService)
                    .recover(userId, emitter, lastEventId);
            verify(recoveryService).recoverAfter(userId, connection, emitter, upperBound);
            verifyNoMoreInteractions(recoveryService, retryFuture);
            verify(retryTaskScheduler).schedule(any(Runnable.class), any(Instant.class));
            verify(emitter).complete();
            verify(emitter, never()).completeWithError(any());

        }
    }

    @Test
    @DisplayName("재시도가 성공해도 기존 연결과 애미터의 타임아웃 설정을 유지한다.")
    void successfulRetryKeepsOriginalEmitterAndTimeout(){
        UUID userId = UUID.randomUUID();
        UUID lastEventId = UUID.randomUUID();
        SseHistoryPosition upperBound = new SseHistoryPosition("1000-0");
        SseHistoryPosition nextPosition = new SseHistoryPosition("1000-1");
        long expectedTimeout = Duration.ofMinutes(30).toMillis();

        stubRecoveryAndRetrySchedule(userId,lastEventId, upperBound);

        SseEmitter emitter = connectionService.connect(userId, lastEventId);

        Map<Connection, SseEmitter> connections = repository.findAllByUserId(userId);

        assertThat(connections).hasSize(1);
        Connection connection = connections.keySet().iterator().next();

        assertThat(emitter.getTimeout()).isEqualTo(expectedTimeout);

        Runnable retryTask = scheduleRetryAfterReadFailure(userId, connection, emitter, upperBound);

        assertThat(emitter.getTimeout()).isEqualTo(expectedTimeout);
        doReturn(nextPosition)
                .when(recoveryService)
                .recoverAfter(userId, connection, emitter, upperBound);

        assertThatCode(retryTask::run)
                .doesNotThrowAnyException();

        Map<Connection, SseEmitter> remainingConnections = repository.findAllByUserId(userId);
        assertThat(remainingConnections).containsOnlyKeys(connection);
        assertThat(remainingConnections.get(connection))
                .isSameAs(emitter);
        assertThat(emitter.getTimeout()).isEqualTo(expectedTimeout);

        verify(recoveryService)
                .recover(userId, emitter, lastEventId);
        verify(recoveryService, times(2))
                .recoverAfter(userId, connection, emitter, upperBound);
        verifyNoMoreInteractions(recoveryService);
        verify(retryTaskScheduler)
                .schedule(any(Runnable.class), any(Instant.class));
        verify(retryFuture, never()).cancel(anyBoolean());
    }

    private void stubRecoveryAndRetrySchedule(
            UUID userId,
            UUID lastEventId,
            SseHistoryPosition upperBound
    ){

        when(retryTaskScheduler.getClock())
                .thenReturn(Clock.fixed(
                        Instant.parse("2026-11-09T00:00:00Z"),
                        ZoneOffset.UTC));

        doReturn(retryFuture)
                .when(retryTaskScheduler)
                .schedule(any(Runnable.class), any(Instant.class));

        when(recoveryService.recover(eq(userId), any(SseEmitter.class), eq(lastEventId)))
                .thenReturn(upperBound);

    }

    private Runnable scheduleRetryAfterReadFailure(
            UUID userId,
            Connection connection,
            SseEmitter emitter,
            SseHistoryPosition upperBound
    ){
        DataAccessResourceFailureException failure = new DataAccessResourceFailureException("테스트용 후속 이력 조회 실패");

        when(recoveryService.recoverAfter(userId, connection, emitter, upperBound))
                .thenThrow(failure);
        assertThatCode(() ->
                coordinator.requestDelivery(userId, connection))
                .doesNotThrowAnyException();

        ArgumentCaptor<Runnable> taskCaptor = ArgumentCaptor.forClass(Runnable.class);
        verify(retryTaskScheduler)
                .schedule(taskCaptor.capture(), any(Instant.class));

        assertThat(repository.findAllByUserId(userId))
                .containsEntry(connection, emitter);
        verify(retryFuture, never()).cancel(anyBoolean());
        return taskCaptor.getValue();
    }


}
