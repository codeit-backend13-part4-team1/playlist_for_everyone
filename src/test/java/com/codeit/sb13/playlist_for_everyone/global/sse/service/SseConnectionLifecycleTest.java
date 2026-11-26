package com.codeit.sb13.playlist_for_everyone.global.sse.service;

import com.codeit.sb13.playlist_for_everyone.domain.auth.repository.UserSessionRepository;
import com.codeit.sb13.playlist_for_everyone.global.config.SseHeartbeatConfig;
import com.codeit.sb13.playlist_for_everyone.global.sse.config.SseProperties;
import com.codeit.sb13.playlist_for_everyone.global.sse.config.SseRetryProperties;
import com.codeit.sb13.playlist_for_everyone.global.sse.event.Connection;
import com.codeit.sb13.playlist_for_everyone.global.sse.event.LoginSession;
import com.codeit.sb13.playlist_for_everyone.global.sse.repository.SseEmitterRepository;
import com.codeit.sb13.playlist_for_everyone.global.sse.repository.impl.InMemorySseEmitterRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.Lifecycle;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.event.ContextClosedEvent;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

/**
 * 실제 Spring 컨텍스트를 닫아 ContextClosedEvent와 조정기의 연결 정리 위임을 확인한다.
 * 서비스 메서드를 직접 호출하는 단위 테스트와 달리 EventListener 등록까지 검증한다.
 * 하트비트 종료 검증에서는 실제 워커와 인메모리 저장소를 사용하고, emitter의 쓰기 잠금으로 느린 전송을 재현한다.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("SSE 연결 종료 생명주기")
public class SseConnectionLifecycleTest {

    @Mock
    SseEmitterRepository repository;

    @Mock
    private SseDeliveryCoordinator deliveryCoordinator;

    @Mock
    private UserSessionRepository userSessionRepository;

    @Test
    @DisplayName("컨텍스트가 종료되면 에미터 종료를 요청하고 연결 정리를 조정기에 위임한다.")
    void contextCloseRequestsCompletionAndDelegatesCleanup(){
        UUID userId = UUID.randomUUID();
        Connection connection = Connection.create();
        SseEmitter emitter = mock(SseEmitter.class);

        when(repository.findAll()).thenReturn(
                Map.of(userId,
                        Map.of(connection, emitter)
                )
        );

        SseProperties properties = new SseProperties(Duration.ofSeconds(15), Duration.ofMinutes(30));

        // try 블록을 벗어날 때 context.close가 호출되며 실제 종료 이벤트가 발행된다.
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()){
            context.registerBean(SseConnectionService.class,
                    ()-> new SseConnectionService(repository, properties, deliveryCoordinator, userSessionRepository));

            context.refresh();
            verify(emitter, never()).complete();
            verify(deliveryCoordinator, never()).cleanupConnection(any(), any());
            verify(repository, never()).delete(any(), any());
        }

        verify(emitter).complete();
        verify(deliveryCoordinator).cleanupConnection(eq(userId), eq(connection));
        verify(deliveryCoordinator, times(1)).cleanupConnection(any(), any());
        verify(repository, never()).delete(any(), any());

    }

    @Test
    @DisplayName("Lifecycle 빈이 종료되기 전에 SSE 에미터 종료를 요청하고 연결 정리를 위임한다.")
    void contextCloseDelegatesCleanupBeforeLifecycleStop(){
        UUID userId = UUID.randomUUID();
        Connection connection = Connection.create();

        SseEmitter emitter = mock(SseEmitter.class);
        Lifecycle lifecycle = mock(Lifecycle.class);

        when(repository.findAll()).thenReturn(
                Map.of(userId,
                        Map.of(connection, emitter)
                )
        );

        when(lifecycle.isRunning()).thenReturn(true);

        SseProperties properties = new SseProperties(Duration.ofSeconds(15), Duration.ofMinutes(30));

        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()){
            context.registerBean(SseConnectionService.class,
                    ()-> new SseConnectionService(repository, properties, deliveryCoordinator, userSessionRepository));
            context.registerBean("testLifecycle", Lifecycle.class, () -> lifecycle);
            context.refresh();

        }

        // 일반 Lifecycle.stop보다 연결 정리 요청이 먼저라는 순서를 검증한다.
        // 스케줄러도 ContextClosedEvent를 받을 수 있으므로 모든 하트비트가 이미 멈췄다고 보장하지는 않는다.
        InOrder completionOrder = inOrder(emitter, lifecycle);
        completionOrder.verify(emitter).complete();
        completionOrder.verify(lifecycle).stop();

        InOrder cleanupOrder = inOrder(deliveryCoordinator, lifecycle);
        cleanupOrder.verify(deliveryCoordinator).cleanupConnection(eq(userId), eq(connection));
        cleanupOrder.verify(lifecycle).stop();
        verify(deliveryCoordinator, times(1)).cleanupConnection(any(), any());
        verify(repository, never()).delete(any(), any());
    }

    @Test
    @DisplayName("종료 이벤트는 하트비트 쓰기가 막혀 있어도 대기 작업을 취소하고 반환한다.")
    void contextCloseEventDiscardsQueuedHeartbeatsWithoutWaitingForWrites() {
        SseEmitterRepository localRepository = new InMemorySseEmitterRepository();
        SseDeliveryCoordinator localCoordinator = new SseDeliveryCoordinator(
                mock(SseRecoveryService.class), mock(TaskScheduler.class),
                new SseRetryProperties(Duration.ofSeconds(1), Duration.ofSeconds(30), 3),
                new SseConnectionCloser(localRepository), localRepository);
        UUID userId = UUID.randomUUID();
        LoginSession session = new LoginSession(UUID.randomUUID());
        Connection first = Connection.create();
        Connection second = Connection.create();
        Connection queued = Connection.create();
        CountDownLatch workersStarted = new CountDownLatch(2);
        CountDownLatch closeAttempted = new CountDownLatch(1);
        CompletableFuture<Void> releaseWrites = new CompletableFuture<>();
        SseEmitter queuedEmitter = mock(SseEmitter.class);
        localCoordinator.register(userId, first, blockingEmitter(workersStarted, closeAttempted, releaseWrites), session);
        localCoordinator.register(userId, second, blockingEmitter(workersStarted, closeAttempted, releaseWrites), session);
        localCoordinator.register(userId, queued, queuedEmitter, session);

        new ApplicationContextRunner()
                .withUserConfiguration(SseHeartbeatConfig.class, SseConnectionService.class)
                .withBean(SseEmitterRepository.class, () -> localRepository)
                .withBean(SseDeliveryCoordinator.class, () -> localCoordinator)
                .withBean(UserSessionRepository.class, () -> userSessionRepository)
                .withBean(SseProperties.class, () -> new SseProperties(Duration.ofSeconds(15), Duration.ofMinutes(30)))
                .withPropertyValues("everyone.sse.heartbeat.pool-size=2", "everyone.sse.heartbeat.queue-capacity=1")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    SseHeartbeatDispatcher dispatcher = context.getBean(SseHeartbeatDispatcher.class);
                    ThreadPoolTaskExecutor workerPool =
                            context.getBean("sseHeartbeatTaskExecutor", ThreadPoolTaskExecutor.class);
                    ExecutorService eventThread = Executors.newSingleThreadExecutor();
                    CountDownLatch eventStarted = new CountDownLatch(1);
                    CountDownLatch eventReturned = new CountDownLatch(1);

                    try {
                        dispatcher.submit(userId, first);
                        dispatcher.submit(userId, second);
                        assertThat(workersStarted.await(5, TimeUnit.SECONDS)).isTrue();
                        dispatcher.submit(userId, queued);
                        assertThat(workerPool.getQueueSize()).isEqualTo(1);

                        Future<?> event = eventThread.submit(() -> {
                            eventStarted.countDown();
                            try {
                                context.publishEvent(new ContextClosedEvent(context.getSourceApplicationContext()));
                            } finally {
                                eventReturned.countDown();
                            }
                        });
                        assertThat(eventStarted.await(5, TimeUnit.SECONDS)).isTrue();
                        assertThat(eventReturned.await(2, TimeUnit.SECONDS))
                                .as("쓰기 대기 중 종료 이벤트 처리가 반환된다. complete() 진입=%s",
                                        closeAttempted.getCount() == 0)
                                .isTrue();
                        event.get(5, TimeUnit.SECONDS);

                        assertThat(workerPool.getQueueSize()).isZero();
                        assertThat(localRepository.findAll()).isEmpty();
                        verify(queuedEmitter).complete();
                        verify(queuedEmitter, never()).send(any(SseEmitter.SseEventBuilder.class));
                    } finally {
                        releaseWrites.complete(null);
                        eventThread.shutdown();
                        if (!eventThread.awaitTermination(5, TimeUnit.SECONDS)) {
                            eventThread.shutdownNow();
                        }
                    }
                });
    }

    private SseEmitter blockingEmitter(
            CountDownLatch workersStarted, CountDownLatch closeAttempted, CompletableFuture<Void> releaseWrites) {
        return new SseEmitter() {
            @Override
            public void send(SseEventBuilder event) {
                writeLock.lock();
                try {
                    workersStarted.countDown();
                    // 느린 쓰기처럼 인터럽트가 와도 테스트에서 해제할 때까지 잠금을 유지한다.
                    releaseWrites.join();
                } finally {
                    writeLock.unlock();
                }
            }

            @Override
            public void complete() {
                closeAttempted.countDown();
                super.complete();
            }
        };
    }
}
