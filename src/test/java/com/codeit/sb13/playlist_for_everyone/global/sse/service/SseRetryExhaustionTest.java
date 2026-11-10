package com.codeit.sb13.playlist_for_everyone.global.sse.service;

import com.codeit.sb13.playlist_for_everyone.global.sse.config.SseProperties;
import com.codeit.sb13.playlist_for_everyone.global.sse.config.SseRetryProperties;
import com.codeit.sb13.playlist_for_everyone.global.sse.event.Connection;
import com.codeit.sb13.playlist_for_everyone.global.sse.history.SseHistoryPosition;
import com.codeit.sb13.playlist_for_everyone.global.sse.repository.SseEmitterRepository;
import com.codeit.sb13.playlist_for_everyone.global.sse.repository.impl.InMemorySseEmitterRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockedConstruction;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ScheduledFuture;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * 실제 조정기·종료 컴포넌트·저장소로 재시도 소진 후 정리 결과를 검증한다.
 * Mock emitter의 complete는 완료 콜백을 자동 실행하지 않는다. 콜백에 의존하지 않는 finally 정리를 확인한다.
 * 예약 작업은 직접 실행하고, HTTP 응답 종료 여부나 실제 지연 시간은 이 테스트에서 검증하지 않는다.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("SSE 재시도 소진 시 연결 서비스와 조정기의 정리")
class SseRetryExhaustionTest {

    @Mock
    private SseRecoveryService recoveryService;

    @Mock
    private TaskScheduler retryTaskScheduler;

    @Mock
    private ScheduledFuture<?> retryFuture;

    @ParameterizedTest(name = "complete() 예외 발생={0}")
    @ValueSource(booleans = {false, true})
    @DisplayName("재시도를 소진하면 완료 콜백 실행이나 complete 성공 여부에 의존하지 않고 연결과 전송 상태를 정리한다.")
    void cleansConnectionOnRetryExhaustionWithoutCompletionCallback(boolean completionFails) {
        UUID userId = UUID.randomUUID();
        UUID lastEventId = UUID.randomUUID();
        SseHistoryPosition upperBound = new SseHistoryPosition("1000-0");
        SseRetryProperties retryProperties = new SseRetryProperties(
                Duration.ofSeconds(1), Duration.ofSeconds(30), 1
        );
        SseProperties properties = new SseProperties(Duration.ofSeconds(15), Duration.ofMinutes(30));
        SseEmitterRepository repository = new InMemorySseEmitterRepository();
        SseDeliveryCoordinator coordinator = new SseDeliveryCoordinator(
                recoveryService, retryTaskScheduler, retryProperties , new SseConnectionCloser(repository), repository
        );
        SseConnectionService service = new SseConnectionService(repository, properties, coordinator);
        DataAccessResourceFailureException failure =
                new DataAccessResourceFailureException("테스트용 후속 이력 조회 실패");

        when(retryTaskScheduler.getClock()).thenReturn(Clock.fixed(
                Instant.parse("2026-11-07T00:00:00Z"), ZoneOffset.UTC
        ));
        doReturn(retryFuture)
                .when(retryTaskScheduler).schedule(any(Runnable.class), any(Instant.class));
        when(recoveryService.recover(
                eq(userId), any(SseEmitter.class), eq(lastEventId)
        )).thenReturn(upperBound);

        try (MockedConstruction<SseEmitter> ignored = mockConstruction(SseEmitter.class)) {
            SseEmitter emitter = service.connect(userId, lastEventId);
            Map<Connection, SseEmitter> connections = repository.findAllByUserId(userId);
            assertThat(connections).hasSize(1);
            Connection connection = connections.keySet().iterator().next();
            assertThat(connections.get(connection)).isSameAs(emitter);

            if (completionFails) {
                doThrow(new IllegalStateException("테스트용 SSE 연결 종료 실패"))
                        .when(emitter).complete();
            }
            when(recoveryService.recoverAfter(userId, connection, emitter, upperBound))
                    .thenThrow(failure);

            assertThatCode(() -> coordinator.requestDelivery(userId, connection))
                    .doesNotThrowAnyException();

            ArgumentCaptor<Runnable> taskCaptor = ArgumentCaptor.forClass(Runnable.class);
            verify(retryTaskScheduler).schedule(taskCaptor.capture(), any(Instant.class));
            verify(emitter, never()).complete();
            assertThat(repository.findAllByUserId(userId)).containsEntry(connection, emitter);

            assertThatCode(taskCaptor.getValue()::run).doesNotThrowAnyException();

            verify(emitter).complete();
            assertThat(repository.findAllByUserId(userId)).isEmpty();
            assertThat(repository.findAll()).doesNotContainKey(userId);

            assertThatCode(() -> coordinator.requestDelivery(userId, connection))
                    .doesNotThrowAnyException();
            assertThatCode(taskCaptor.getValue()::run).doesNotThrowAnyException();

            verify(recoveryService).recover(userId, emitter, lastEventId);
            verify(recoveryService, times(2)).recoverAfter(userId, connection, emitter, upperBound);
            verifyNoMoreInteractions(recoveryService);
            verify(retryTaskScheduler).schedule(any(Runnable.class), any(Instant.class));
            verify(emitter).complete();
            verify(emitter, never()).completeWithError(any());
        }
    }
}
