package com.codeit.sb13.playlist_for_everyone.global.config;

import com.codeit.sb13.playlist_for_everyone.domain.auth.repository.UserSessionRepository;
import com.codeit.sb13.playlist_for_everyone.global.sse.config.SseRetryProperties;
import com.codeit.sb13.playlist_for_everyone.global.sse.event.Connection;
import com.codeit.sb13.playlist_for_everyone.global.sse.event.LoginSession;
import com.codeit.sb13.playlist_for_everyone.global.sse.repository.SseEmitterRepository;
import com.codeit.sb13.playlist_for_everyone.global.sse.repository.impl.InMemorySseEmitterRepository;
import com.codeit.sb13.playlist_for_everyone.global.sse.scheduler.SseHeartbeatScheduler;
import com.codeit.sb13.playlist_for_everyone.global.sse.service.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@DisplayName("SSE 하트비트 전용 실행기")
class SseHeartbeatConfigTest {

    private final InMemorySseEmitterRepository repository = new InMemorySseEmitterRepository();
    private final UUID userId = UUID.randomUUID();
    private final LoginSession loginSession = new LoginSession(UUID.randomUUID());
    private final SseDeliveryCoordinator coordinator = new SseDeliveryCoordinator(
            mock(SseRecoveryService.class), mock(TaskScheduler.class),
            new SseRetryProperties(Duration.ofSeconds(1), Duration.ofSeconds(30), 3),
            new SseConnectionCloser(repository), repository);

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(SseHeartbeatConfig.class)
            .withBean(SseEmitterRepository.class, () -> repository)
            .withBean(SseDeliveryCoordinator.class, () -> coordinator)
            .withPropertyValues("everyone.sse.heartbeat.pool-size=2", "everyone.sse.heartbeat.queue-capacity=1");

    @Test
    @DisplayName("대기열 크기를 0으로 설정할 수 있다.")
    void acceptsZeroQueueCapacity() {
        contextRunner.withPropertyValues("everyone.sse.heartbeat.queue-capacity=0").run(context -> {
            assertThat(context).hasNotFailed();
            ThreadPoolTaskExecutor executor = context.getBean("sseHeartbeatTaskExecutor", ThreadPoolTaskExecutor.class);
            assertThat(executor.getQueueCapacity()).isZero();
        });
    }

    @ParameterizedTest
    @CsvSource({"1, 1", "2, -1"})
    @DisplayName("워커가 2개 미만이거나 대기열 크기가 음수면 시작에 실패한다.")
    void rejectsInvalidLimits(int poolSize, int queueCapacity) {
        contextRunner.withPropertyValues("everyone.sse.heartbeat.pool-size=" + poolSize,
                        "everyone.sse.heartbeat.queue-capacity=" + queueCapacity)
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure()).hasRootCauseInstanceOf(IllegalArgumentException.class);
                });
    }

    @Test
    @DisplayName("중복 제출을 건너뛰고 포화로 거절된 연결은 다음 전송에 다시 제출할 수 있다.")
    void limitsConcurrentAndQueuedHeartbeatsAndAllowsRetryAfterRejection() {
        contextRunner.run(context -> {
            assertThat(context).hasNotFailed();
            SseHeartbeatDispatcher dispatcher = context.getBean(SseHeartbeatDispatcher.class);
            ThreadPoolTaskExecutor executor = context.getBean("sseHeartbeatTaskExecutor", ThreadPoolTaskExecutor.class);
            CountDownLatch workersStarted = new CountDownLatch(2);
            CountDownLatch releaseWorkers = new CountDownLatch(1);
            CountDownLatch queuedSendStarted = new CountDownLatch(1);
            CountDownLatch retriedSendStarted = new CountDownLatch(1);

            Connection first = register(mock(SseEmitter.class), workersStarted, releaseWorkers);
            Connection second = register(mock(SseEmitter.class), workersStarted, releaseWorkers);
            Connection queued = register(mock(SseEmitter.class), queuedSendStarted, new CountDownLatch(0));
            SseEmitter retriedEmitter = mock(SseEmitter.class);
            Connection retried = register(retriedEmitter, retriedSendStarted, new CountDownLatch(0));

            try {
                dispatcher.submit(userId, first);
                dispatcher.submit(userId, second);
                assertThat(workersStarted.await(5, TimeUnit.SECONDS)).isTrue();
                assertThat(executor.getActiveCount()).isEqualTo(2);

                dispatcher.submit(userId, first);
                assertThat(executor.getQueueSize()).isZero();
                dispatcher.submit(userId, queued);
                assertThat(executor.getQueueSize()).isEqualTo(1);

                dispatcher.submit(userId, retried);
                verifyNoInteractions(retriedEmitter);
                assertThat(executor.getQueueSize()).isEqualTo(1);

                releaseWorkers.countDown();
                assertThat(queuedSendStarted.await(5, TimeUnit.SECONDS)).isTrue();
                dispatcher.submit(userId, retried);
                assertThat(retriedSendStarted.await(5, TimeUnit.SECONDS)).isTrue();
            } finally {
                releaseWorkers.countDown();
            }
        });
    }

    @Test
    @DisplayName("대기 중인 하트비트에 종료가 요청되면 종료를 먼저 처리한다.")
    void prioritizesClosureOverQueuedHeartbeat() {
        contextRunner.run(context -> {
            assertThat(context).hasNotFailed();
            SseHeartbeatDispatcher dispatcher = context.getBean(SseHeartbeatDispatcher.class);
            CountDownLatch workersStarted = new CountDownLatch(2);
            CountDownLatch releaseWorkers = new CountDownLatch(1);
            Connection first = register(mock(SseEmitter.class), workersStarted, releaseWorkers);
            Connection second = register(mock(SseEmitter.class), workersStarted, releaseWorkers);
            Connection queued = Connection.create();
            SseEmitter queuedEmitter = mock(SseEmitter.class);
            repository.save(userId, queued, queuedEmitter, loginSession);

            try {
                dispatcher.submit(userId, first);
                dispatcher.submit(userId, second);
                assertThat(workersStarted.await(5, TimeUnit.SECONDS)).isTrue();

                dispatcher.submit(userId, queued);
                dispatcher.requestClose(userId, queued);
                verifyNoInteractions(queuedEmitter);

                releaseWorkers.countDown();

                await().atMost(Duration.ofSeconds(5)).untilAsserted(() ->
                        assertThat(repository.findByUserIdAndConnection(userId, queued)).isEmpty());
                verify(queuedEmitter).complete();
                verify(queuedEmitter, never()).send(any(SseEmitter.SseEventBuilder.class));
            } finally {
                releaseWorkers.countDown();
            }
        });
    }

    @Test
    @DisplayName("다음 세션 조회가 실패해도 포화로 거절된 연결 종료를 재시도한다.")
    void retriesRejectedClosureBeforeHeartbeatsWhenSessionLookupFails(){
        contextRunner.run(context -> {
            assertThat(context).hasNotFailed();
            SseHeartbeatDispatcher dispatcher = context.getBean(SseHeartbeatDispatcher.class);

            ThreadPoolTaskExecutor executor = context.getBean("sseHeartbeatTaskExecutor", ThreadPoolTaskExecutor.class);

            CountDownLatch firstTwoStarted = new CountDownLatch(2);
            CountDownLatch queuedStarted = new CountDownLatch(1);
            CountDownLatch releaseFirst = new CountDownLatch(1);
            CountDownLatch releaseRemaining = new CountDownLatch(1);

            Connection first = register(mock(SseEmitter.class), firstTwoStarted, releaseFirst);
            Connection second = register(mock(SseEmitter.class), firstTwoStarted, releaseRemaining);
            Connection queued = register(mock(SseEmitter.class), queuedStarted, releaseRemaining);

            UUID revokedUserId = UUID.randomUUID();
            LoginSession revokedSession = new LoginSession(UUID.randomUUID());
            Connection revokedConnection = Connection.create();
            SseEmitter revokedEmitter = mock(SseEmitter.class);
            coordinator.register(revokedUserId, revokedConnection, revokedEmitter, revokedSession);
            UserSessionRepository sessions = mock(UserSessionRepository.class);
            when(sessions.isCurrent(userId, loginSession.id())).thenReturn(true);
            when(sessions.isCurrent(revokedUserId, revokedSession.id()))
                    .thenReturn(false)
                    .thenThrow(new QueryTimeoutException("테스트용 Redis 조회 시간 초과"));

            SseDeliveryService deliveryService = new SseDeliveryService(repository, coordinator, sessions, dispatcher);
            SseHeartbeatScheduler scheduler = new SseHeartbeatScheduler(deliveryService);

            try {
                dispatcher.submit(userId, first);
                dispatcher.submit(userId, second);
                assertThat(firstTwoStarted.await(5, TimeUnit.SECONDS)).isTrue();

                dispatcher.submit(userId, queued);
                assertThat(executor.getQueueSize()).isEqualTo(1);

                deliveryService.revalidateLocalSessions();
                verify(sessions).isCurrent(revokedUserId, revokedSession.id());
                verify(revokedEmitter, never()).complete();

                assertThat(repository.findByUserIdAndConnection(revokedUserId, revokedConnection))
                        .containsSame(revokedEmitter);

                releaseFirst.countDown();
                assertThat(queuedStarted.await(5, TimeUnit.SECONDS)).isTrue();
                assertThat(executor.getActiveCount()).isEqualTo(2);
                assertThat(executor.getQueueSize()).isZero();

                scheduler.sendHeartbeat();
                verify(sessions, times(2))
                        .isCurrent(revokedUserId, revokedSession.id());

                releaseRemaining.countDown();
                await().atMost(Duration.ofSeconds(5))
                        .untilAsserted(() ->
                                assertThat(repository.findByUserIdAndConnection(revokedUserId, revokedConnection))
                                        .as("이미 폐기를 확인한 연결은 다음 Redis 조회가 실패해도 종료된다.")
                                        .isEmpty());
                verify(revokedEmitter).complete();
            } finally {
                releaseFirst.countDown();
                releaseRemaining.countDown();
            }

        });
    }

    @Test
    @DisplayName("이미 제거된 연결의 보류 종료 요청은 대기열을 사용하지 않는다.")
    void discardsPendingClosureWhenTheConnectionIsAlreadyGone() {
        contextRunner.run(context -> {
            assertThat(context).hasNotFailed();
            SseHeartbeatDispatcher dispatcher = context.getBean(SseHeartbeatDispatcher.class);
            ThreadPoolTaskExecutor executor =
                    context.getBean("sseHeartbeatTaskExecutor", ThreadPoolTaskExecutor.class);
            CountDownLatch firstTwoStarted = new CountDownLatch(2);
            CountDownLatch queuedStarted = new CountDownLatch(1);
            CountDownLatch releaseFirst = new CountDownLatch(1);
            CountDownLatch releaseRemaining = new CountDownLatch(1);

            Connection first = register(mock(SseEmitter.class), firstTwoStarted, releaseFirst);
            Connection second = register(mock(SseEmitter.class), firstTwoStarted, releaseRemaining);
            Connection queued = register(mock(SseEmitter.class), queuedStarted, releaseRemaining);
            Connection disconnected = Connection.create();
            SseEmitter disconnectedEmitter = mock(SseEmitter.class);
            coordinator.register(userId, disconnected, disconnectedEmitter, loginSession);

            try {
                dispatcher.submit(userId, first);
                dispatcher.submit(userId, second);
                assertThat(firstTwoStarted.await(5, TimeUnit.SECONDS)).isTrue();
                dispatcher.submit(userId, queued);
                assertThat(executor.getQueueSize()).isEqualTo(1);

                dispatcher.requestClose(userId, disconnected);
                coordinator.cleanupConnection(userId, disconnected);
                assertThat(repository.findByUserIdAndConnection(userId, disconnected)).isEmpty();

                releaseFirst.countDown();
                assertThat(queuedStarted.await(5, TimeUnit.SECONDS)).isTrue();
                assertThat(executor.getActiveCount()).isEqualTo(2);

                dispatcher.retryPendingClosures();

                assertThat(executor.getQueueSize()).isZero();
                verifyNoInteractions(disconnectedEmitter);
            } finally {
                releaseFirst.countDown();
                releaseRemaining.countDown();
            }
        });
    }

    private Connection register(SseEmitter emitter, CountDownLatch started, CountDownLatch release) throws Exception {
        doAnswer(invocation -> {
            started.countDown();
            assertThat(release.await(10, TimeUnit.SECONDS)).isTrue();
            return null;
        }).when(emitter).send(any(SseEmitter.SseEventBuilder.class));
        Connection connection = Connection.create();
        repository.save(userId, connection, emitter, loginSession);
        return connection;
    }
}
