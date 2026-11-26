package com.codeit.sb13.playlist_for_everyone.global.sse.service;

import com.codeit.sb13.playlist_for_everyone.domain.auth.repository.UserSessionRepository;
import com.codeit.sb13.playlist_for_everyone.global.config.SseHeartbeatConfig;
import com.codeit.sb13.playlist_for_everyone.global.sse.config.SseRetryProperties;
import com.codeit.sb13.playlist_for_everyone.global.sse.event.Connection;
import com.codeit.sb13.playlist_for_everyone.global.sse.event.Event;
import com.codeit.sb13.playlist_for_everyone.global.sse.event.LoginSession;
import com.codeit.sb13.playlist_for_everyone.global.sse.event.SseEvent;
import com.codeit.sb13.playlist_for_everyone.global.sse.repository.SseEmitterRepository;
import com.codeit.sb13.playlist_for_everyone.global.sse.repository.impl.InMemorySseEmitterRepository;
import com.codeit.sb13.playlist_for_everyone.global.sse.scheduler.SseHeartbeatScheduler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * 전송 서비스가 수신자의 연결을 선택하고, 일부 실패에도 나머지 전송을 계속하는지 검증한다.
 * 전송 테스트는 저장소와 emitter를 Mock으로 대체한다.
 * 세션별 조회 횟수 검증은 실제 메모리 저장소에 여러 탭을 등록한다.
 * 실제 HTTP 전송과 Spring MVC 메시지 변환기는 실행하지 않는다.
 *
 * <p>실패 후 계속 진행하는 테스트에서는 LinkedHashMap으로 실패 연결을 먼저 배치한다.
 * 하트비트 테스트도 같은 사용자와 다른 사용자까지 전송이 이어지는지를 확인한다.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("SSE 로컬 전송 서비스")
class SseDeliveryServiceTest {

    @Mock
    private SseEmitterRepository repository;

    @Mock
    private SseDeliveryCoordinator deliveryCoordinator;

    @Mock
    private UserSessionRepository userSessionRepository;

    private SseDeliveryService service;
    private final UUID receiverId = UUID.randomUUID();
    private final Connection connection = Connection.create();

    @BeforeEach
    void setUp() {
        service = createServiceWithSynchronousHeartbeat(repository);
    }

    private SseDeliveryService createServiceWithSynchronousHeartbeat(SseEmitterRepository emitterRepository) {
        SseHeartbeatWorker worker = new SseHeartbeatWorker(emitterRepository, deliveryCoordinator);
        return new SseDeliveryService(
                emitterRepository, deliveryCoordinator, userSessionRepository,
                new SseHeartbeatDispatcher(worker, emitterRepository));
    }

    private void stubHeartbeatConnections(Map<UUID, Map<Connection, SseEmitter>> connectionsByUser) {
        when(repository.findAll()).thenReturn(connectionsByUser);
        connectionsByUser.forEach((userId, connections) -> connections.forEach((connection, emitter) ->
                when(repository.findByUserIdAndConnection(userId, connection)).thenReturn(Optional.of(emitter))));
    }

    @Test
    @DisplayName("수신자의 연결을 조회하여 조정기에 전송을 요청한다.")
    void requestsDeliveryForReceiverConnection() throws IOException {
        SseEmitter emitter = mock(SseEmitter.class);
        when(repository.findAllByUserId(receiverId))
                .thenReturn(Map.of(connection, emitter));

        SseEvent<Map<String, String>> event = new SseEvent<>(
                UUID.randomUUID(),
                receiverId,
                Event.NOTIFICATIONS.getName(),
                Map.of("message", "테스트 알림")
        );

        service.sendToLocalConnections(event);

        verify(repository).findAllByUserId(receiverId);
        verify(deliveryCoordinator).requestDelivery(receiverId, connection);
        verify(deliveryCoordinator, times(1)).requestDelivery(any(), any());
        verify(emitter, never()).send(any(SseEmitter.SseEventBuilder.class));
    }

    @Test
    @DisplayName("수신자의 연결이 여러 개이면 각 연결에 대해 조정기에 전송을 요청한다")
    void requestsDeliveryForAllReceiverConnections() throws IOException {
        Connection secondConnection = Connection.create();

        SseEmitter firstEmitter = mock(SseEmitter.class);
        SseEmitter secondEmitter = mock(SseEmitter.class);

        when(repository.findAllByUserId(receiverId)).thenReturn(Map.of(
                connection, firstEmitter,
                secondConnection, secondEmitter
        ));

        SseEvent<Map<String, String>> event = new SseEvent<>(
                UUID.randomUUID(),
                receiverId,
                Event.NOTIFICATIONS.getName(),
                Map.of("message", "테스트 알림")
        );

        service.sendToLocalConnections(event);

        verify(repository).findAllByUserId(receiverId);
        verify(deliveryCoordinator).requestDelivery(receiverId, connection);
        verify(deliveryCoordinator).requestDelivery(receiverId, secondConnection);
        verify(deliveryCoordinator, times(2)).requestDelivery(any(), any());

        verify(firstEmitter, never()).send(any(SseEmitter.SseEventBuilder.class));
        verify(secondEmitter, never()).send(any(SseEmitter.SseEventBuilder.class));
    }

    @Test
    @DisplayName("수신자의 연결이 없으면 조정기 전송이나 연결 삭제를 요청하지 않는다.")
    void sendEventWithoutConnectionsDoesNotFail(){
        UUID receiverId = UUID.randomUUID();

        when(repository.findAllByUserId(receiverId))
                .thenReturn(Map.of());

        SseEvent<Map<String, String>> event = new SseEvent<>(
                UUID.randomUUID(),
                receiverId,
                Event.NOTIFICATIONS.getName(),
                Map.of("message", "테스트 알림")
        );

        assertThatCode(() -> service.sendToLocalConnections(event))
                .doesNotThrowAnyException();

        verify(repository).findAllByUserId(receiverId);
        verify(repository, never()).delete(any(), any());
        verifyNoInteractions(deliveryCoordinator);
    }

    @Test
    @DisplayName("조정기 요청 중 I/O 오류가 발생하면 실패한 연결의 종료를 위임하고 다음 연결을 처리한다.")
    void ioFailureClosesOnlyFailedConnectionAndContinuesSending() throws IOException {

        UUID receiverId = UUID.randomUUID();

        Connection failedConnection = Connection.create();
        Connection healthyConnection = Connection.create();

        SseEmitter failedEmitter = mock(SseEmitter.class);
        SseEmitter healthyEmitter = mock(SseEmitter.class);

        // 순서를 고정해야 정상 연결이 우연히 먼저 처리된 결과를 '실패 후 계속 전송'으로 오해하지 않는다.
        Map<Connection, SseEmitter> connections = new LinkedHashMap<>();
        connections.put(failedConnection, failedEmitter);
        connections.put(healthyConnection, healthyEmitter);

        when(repository.findAllByUserId(receiverId)).thenReturn(connections);

        doThrow(new UncheckedIOException("테스트용 전송 실패", new IOException("전송 실패")))
                .when(deliveryCoordinator)
                .requestDelivery(receiverId, failedConnection);

        SseEvent<Map<String, String>> event = new SseEvent<>(
                UUID.randomUUID(),
                receiverId,
                Event.NOTIFICATIONS.getName(),
                Map.of("message", "테스트 알림")
        );

        assertThatCode(() -> service.sendToLocalConnections(event))
                .doesNotThrowAnyException();

        verify(deliveryCoordinator).requestDelivery(receiverId, failedConnection);
        verify(deliveryCoordinator).closeConnection(receiverId, failedConnection);
        verify(deliveryCoordinator).requestDelivery(receiverId, healthyConnection);
        verifyNoMoreInteractions(deliveryCoordinator);
        verify(repository, never()).delete(any(), any());
    }

    @Test
    @DisplayName("조정기 요청 중 상태 예외가 발생하면 실패한 연결의 종료를 위임하고 다음 연결을 처리한다.")
    void illegalStateFailureClosesOnlyFailedConnectionAndContinuesSending() throws IOException {
        Connection failedConnection = Connection.create();
        Connection healthyConnection = Connection.create();

        SseEmitter failedEmitter = mock(SseEmitter.class);
        SseEmitter healthyEmitter = mock(SseEmitter.class);

        Map<Connection, SseEmitter> connections = new LinkedHashMap<>();
        connections.put(failedConnection, failedEmitter);
        connections.put(healthyConnection, healthyEmitter);

        when(repository.findAllByUserId(receiverId)).thenReturn(connections);

        doThrow(new IllegalStateException("이미 종료된 연결"))
                .when(deliveryCoordinator)
                .requestDelivery(receiverId, failedConnection);

        SseEvent<Map<String, String>> event = new SseEvent<>(
                UUID.randomUUID(),
                receiverId,
                Event.NOTIFICATIONS.getName(),
                Map.of("message", "테스트 알림")
        );

        assertThatCode(() -> service.sendToLocalConnections(event))
                .doesNotThrowAnyException();

        verify(deliveryCoordinator).requestDelivery(receiverId, failedConnection);
        verify(deliveryCoordinator).closeConnection(receiverId, failedConnection);
        verify(deliveryCoordinator).requestDelivery(receiverId, healthyConnection);
        verifyNoMoreInteractions(deliveryCoordinator);
        verify(repository, never()).delete(any(), any());
    }

    @Test
    @DisplayName("등록된 연결에 이벤트 ID 없는 하트비트 주석을 전송한다.")
    void sendHeartbeatCommentToLocalConnection() throws IOException {
        UUID userId = UUID.randomUUID();
        Connection connection = Connection.create();

        SseEmitter emitter = mock(SseEmitter.class);
        stubHeartbeatConnections(Map.of(userId, Map.of(connection, emitter)));

        service.sendHeartbeatToLocalConnections();

        ArgumentCaptor<SseEmitter.SseEventBuilder> eventCaptor = ArgumentCaptor.forClass(SseEmitter.SseEventBuilder.class);
        verify(emitter).send(eventCaptor.capture());

        // ID나 event 필드 없는 주석 하나만 전송하는지 확인하여 재연결 커서에 영향을 주지 않게 한다.
        assertThat(eventCaptor.getValue().build())
                .extracting(part -> part.getData())
                .containsExactly(":heartbeat\n\n");

    }

    @Test
    @DisplayName("여러 사용자의 모든 연결에 하트비트를 전송한다.")
    void sendHeartbeatToAllLocalConnections() throws IOException {
        UUID firstUserId = UUID.randomUUID();
        UUID secondUserId = UUID.randomUUID();

        SseEmitter firstEmitter = mock(SseEmitter.class);
        SseEmitter secondEmitter = mock(SseEmitter.class);
        SseEmitter thirdEmitter = mock(SseEmitter.class);

        stubHeartbeatConnections(Map.of(
                firstUserId, Map.of(
                        Connection.create(), firstEmitter,
                        Connection.create(), secondEmitter
                ),
                secondUserId, Map.of(Connection.create(), thirdEmitter)
        ));

        service.sendHeartbeatToLocalConnections();

        verify(firstEmitter).send(any(SseEmitter.SseEventBuilder.class));
        verify(secondEmitter).send(any(SseEmitter.SseEventBuilder.class));
        verify(thirdEmitter).send(any(SseEmitter.SseEventBuilder.class));

        verify(repository, never()).delete(any(), any());
    }

    @Test
    @DisplayName("등록된 연결이 없으면 하트비트 전송은 예외나 연결 삭제 요청 없이 종료한다.")
    void sendHeartbeatWithoutConnectionsDoesNotFail(){
        when(repository.findAll()).thenReturn(Map.of());

        assertThatCode(()-> service.sendHeartbeatToLocalConnections())
                .doesNotThrowAnyException();

        verify(repository).findAll();
        verify(repository, never()).delete(any(), any());
    }

    @Test
    @DisplayName("하트비트 I/O 실패 시 실패한 연결만 정리를 위임하고 나머지 전송을 계속한다.")
    void heartbeatIoFailureDelegatesCleanupOnlyForFailedConnectionAndContinuesSending() throws IOException {
        UUID firstUserId = UUID.randomUUID();
        UUID secondUserId = UUID.randomUUID();

        Connection failedConnection = Connection.create();
        Connection healthyConnection = Connection.create();

        SseEmitter failedEmitter = mock(SseEmitter.class);
        SseEmitter healthyEmitter = mock(SseEmitter.class);
        SseEmitter otherUserEmitter = mock(SseEmitter.class);

        Map<Connection, SseEmitter> firstUserConnections = new LinkedHashMap<>();

        firstUserConnections.put(failedConnection, failedEmitter);
        firstUserConnections.put(healthyConnection, healthyEmitter);

        Map<UUID, Map<Connection, SseEmitter>> connectionsByUser = new LinkedHashMap<>();

        connectionsByUser.put(firstUserId, firstUserConnections);
        connectionsByUser.put(secondUserId, Map.of(Connection.create(), otherUserEmitter));

        stubHeartbeatConnections(connectionsByUser);

        doThrow(new IOException("하트비트 전송 실패"))
                .when(failedEmitter)
                .send(any(SseEmitter.SseEventBuilder.class));

        assertThatCode(()-> service.sendHeartbeatToLocalConnections())
                .doesNotThrowAnyException();

        verify(failedEmitter).send(any(SseEmitter.SseEventBuilder.class));
        verify(deliveryCoordinator).cleanupConnection(eq(firstUserId), eq(failedConnection));
        verify(deliveryCoordinator, times(1)).cleanupConnection(any(), any());
        verify(repository, never()).delete(any(), any());

        verify(healthyEmitter).send(any(SseEmitter.SseEventBuilder.class));
        verify(otherUserEmitter).send(any(SseEmitter.SseEventBuilder.class));

    }

    @Test
    @DisplayName("하트비트 상태 예외 발생 시 실패한 연결만 정리를 위임하고 나머지 전송을 계속한다.")
    void heartbeatIllegalStateFailureDelegatesCleanupOnlyForFailedConnectionAndContinuesSending() throws IOException {
        UUID firstUserId = UUID.randomUUID();
        UUID secondUserId = UUID.randomUUID();

        Connection failedConnection = Connection.create();
        Connection healthyConnection = Connection.create();

        SseEmitter failedEmitter = mock(SseEmitter.class);
        SseEmitter healthyEmitter = mock(SseEmitter.class);
        SseEmitter otherUserEmitter = mock(SseEmitter.class);

        Map<Connection, SseEmitter> firstUserConnections = new LinkedHashMap<>();

        firstUserConnections.put(failedConnection, failedEmitter);
        firstUserConnections.put(healthyConnection, healthyEmitter);

        Map<UUID, Map<Connection, SseEmitter>> connectionsByUser = new LinkedHashMap<>();

        connectionsByUser.put(firstUserId, firstUserConnections);
        connectionsByUser.put(secondUserId, Map.of(Connection.create(), otherUserEmitter));

        stubHeartbeatConnections(connectionsByUser);

        doThrow(new IllegalStateException("이미 종료된 연결"))
                .when(failedEmitter)
                .send(any(SseEmitter.SseEventBuilder.class));

        assertThatCode(()-> service.sendHeartbeatToLocalConnections())
                .doesNotThrowAnyException();

        verify(failedEmitter).send(any(SseEmitter.SseEventBuilder.class));
        verify(deliveryCoordinator).cleanupConnection(eq(firstUserId), eq(failedConnection));
        verify(deliveryCoordinator, times(1)).cleanupConnection(any(), any());
        verify(repository, never()).delete(any(), any());

        verify(healthyEmitter).send(any(SseEmitter.SseEventBuilder.class));
        verify(otherUserEmitter).send(any(SseEmitter.SseEventBuilder.class));

    }
    @Test
    @DisplayName("같은 로그인 세션의 여러 탭을 한 번만 조회하고 폐기된 세션의 탭만 닫는다.")
    void revalidatesEachLoginSessionOnceAndClosesOnlyRevokedTabs() {
        SseEmitterRepository localRepository = new InMemorySseEmitterRepository();
        SseDeliveryService localService = createServiceWithSynchronousHeartbeat(localRepository);
        UUID userId = UUID.randomUUID();
        UUID otherUserId = UUID.randomUUID();
        LoginSession revokedSession = new LoginSession(UUID.randomUUID());
        LoginSession currentSession = new LoginSession(UUID.randomUUID());
        LoginSession otherUserSession = new LoginSession(UUID.randomUUID());
        Connection firstTab = Connection.create();
        Connection secondTab = Connection.create();

        localRepository.save(userId, firstTab, new SseEmitter(), revokedSession);
        localRepository.save(userId, secondTab, new SseEmitter(), revokedSession);
        localRepository.save(userId, Connection.create(), new SseEmitter(), currentSession);
        localRepository.save(otherUserId, Connection.create(), new SseEmitter(), otherUserSession);
        when(userSessionRepository.isCurrent(userId, revokedSession.id())).thenReturn(false);
        when(userSessionRepository.isCurrent(userId, currentSession.id())).thenReturn(true);
        when(userSessionRepository.isCurrent(otherUserId, otherUserSession.id())).thenReturn(true);

        localService.revalidateLocalSessions();

        verify(userSessionRepository).isCurrent(userId, revokedSession.id());
        verify(userSessionRepository).isCurrent(userId, currentSession.id());
        verify(userSessionRepository).isCurrent(otherUserId, otherUserSession.id());
        verifyNoMoreInteractions(userSessionRepository);
        InOrder firstTabOrder = inOrder(deliveryCoordinator);
        firstTabOrder.verify(deliveryCoordinator).stopDelivery(userId, firstTab);
        firstTabOrder.verify(deliveryCoordinator).closeConnection(userId, firstTab);
        InOrder secondTabOrder = inOrder(deliveryCoordinator);
        secondTabOrder.verify(deliveryCoordinator).stopDelivery(userId, secondTab);
        secondTabOrder.verify(deliveryCoordinator).closeConnection(userId, secondTab);
        verifyNoMoreInteractions(deliveryCoordinator);
    }

    @Test
    @DisplayName("세션 조회 실패 중에도 하트비트를 보내고 다른 세션을 확인하며 다음 점검에서 다시 조회한다.")
    void keepsConnectionOnLookupFailureAndClosesItAfterRevocationIsConfirmed() throws IOException {
        UUID otherUserId = UUID.randomUUID();
        LoginSession loginSession = new LoginSession(UUID.randomUUID());
        LoginSession otherUserSession = new LoginSession(UUID.randomUUID());
        SseEmitter emitter = mock(SseEmitter.class);
        Connection otherConnection = Connection.create();
        SseEmitter otherEmitter = mock(SseEmitter.class);
        Map<UUID, Set<LoginSession>> sessions = new LinkedHashMap<>();
        sessions.put(receiverId, Set.of(loginSession));
        sessions.put(otherUserId, Set.of(otherUserSession));

        stubHeartbeatConnections(Map.of(
                receiverId, Map.of(connection, emitter),
                otherUserId, Map.of(otherConnection, otherEmitter)
        ));
        when(repository.findAllLoginSessions()).thenReturn(sessions);
        when(userSessionRepository.isCurrent(receiverId, loginSession.id()))
                .thenThrow(new QueryTimeoutException("테스트용 Redis 조회 시간 초과"))
                .thenReturn(false);
        when(userSessionRepository.isCurrent(otherUserId, otherUserSession.id())).thenReturn(true);
        when(repository.findByUserIdAndLoginSessionId(receiverId, loginSession))
                .thenReturn(Map.of(connection, emitter));
        SseHeartbeatScheduler scheduler = new SseHeartbeatScheduler(service);

        scheduler.sendHeartbeat();

        verify(emitter).send(any(SseEmitter.SseEventBuilder.class));
        verify(otherEmitter).send(any(SseEmitter.SseEventBuilder.class));
        verify(userSessionRepository).isCurrent(otherUserId, otherUserSession.id());
        verifyNoInteractions(deliveryCoordinator);

        scheduler.sendHeartbeat();

        verify(userSessionRepository, times(2)).isCurrent(receiverId, loginSession.id());
        verify(emitter, times(2)).send(any(SseEmitter.SseEventBuilder.class));
        InOrder closureOrder = inOrder(deliveryCoordinator);
        closureOrder.verify(deliveryCoordinator).stopDelivery(receiverId, connection);
        closureOrder.verify(deliveryCoordinator).closeConnection(receiverId, connection);
        verifyNoMoreInteractions(deliveryCoordinator);
    }

    @Test
    @DisplayName("느린 SSE 연결이 전송 중이어도 정상 연결의 하트비트와 세션 재검증을 진행한다.")
    void continuesHeartbeatAndSessionRevalidationWhileOneConnectionIsBlocked() throws IOException {
        Connection slowConnection = Connection.create();
        Connection healthyConnection = Connection.create();

        LoginSession loginSession = new LoginSession(UUID.randomUUID());

        SseEmitter slowEmitter = mock(SseEmitter.class);
        SseEmitter healthyEmitter = mock(SseEmitter.class);

        Map<Connection, SseEmitter> connections = new LinkedHashMap<>();
        connections.put(slowConnection, slowEmitter);
        connections.put(healthyConnection, healthyEmitter);

        stubHeartbeatConnections(Map.of(receiverId, connections));
        when(repository.findAllLoginSessions()).thenReturn(Map.of(receiverId, Set.of(loginSession)));

        CountDownLatch slowSendStarted = new CountDownLatch(1);
        CountDownLatch releaseSlowSend = new CountDownLatch(1);
        CountDownLatch healthySendCalled = new CountDownLatch(1);
        CountDownLatch sessionLookupCalled = new CountDownLatch(1);
        AtomicReference<String> sendThreadName = new AtomicReference<>();

        doAnswer(invocation -> {
            sendThreadName.set(Thread.currentThread().getName());
            slowSendStarted.countDown();
            assertThat(releaseSlowSend.await(10, TimeUnit.SECONDS))
                    .as("테스트에서 느린 연결의 대기를 해제했다.")
                    .isTrue();
            return null;
        }).when(slowEmitter).send(any(SseEmitter.SseEventBuilder.class));

        doAnswer(invocation ->{
            healthySendCalled.countDown();
            return null;
        }).when(healthyEmitter).send(any(SseEmitter.SseEventBuilder.class));

        when(userSessionRepository.isCurrent(receiverId, loginSession.id()))
                .thenAnswer(invocation ->{
                    sessionLookupCalled.countDown();
                    return true;
                });

        new ApplicationContextRunner()
                .withUserConfiguration(SseHeartbeatConfig.class)
                .withBean(SseEmitterRepository.class, () -> repository)
                .withBean(SseDeliveryCoordinator.class, () -> deliveryCoordinator)
                .withPropertyValues("everyone.sse.heartbeat.pool-size=2", "everyone.sse.heartbeat.queue-capacity=1")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    SseDeliveryService asyncService = new SseDeliveryService(
                            repository, deliveryCoordinator, userSessionRepository,
                            context.getBean(SseHeartbeatDispatcher.class));
                    SseHeartbeatScheduler scheduler = new SseHeartbeatScheduler(asyncService);
                    ExecutorService schedulerThread = Executors.newSingleThreadExecutor();
                    Future<?> tick = schedulerThread.submit(scheduler::sendHeartbeat);

                    try {
                        assertThat(slowSendStarted.await(5, TimeUnit.SECONDS))
                                .as("느린 연결의 send()에 진입했다.")
                                .isTrue();
                        assertThat(sendThreadName.get()).startsWith("sse-heartbeat-worker-");
                        assertThat(healthySendCalled.await(2, TimeUnit.SECONDS))
                                .as("느린 전송 대기 중 정상 연결에 하트비트를 보냈다.")
                                .isTrue();
                        assertThat(sessionLookupCalled.await(2, TimeUnit.SECONDS))
                                .as("느린 전송 대기 중 세션을 조회했다.")
                                .isTrue();
                        tick.get(2, TimeUnit.SECONDS);
                    } finally {
                        releaseSlowSend.countDown();
                        schedulerThread.shutdownNow();
                    }
                });
    }

    @Test
    @DisplayName("전송 중인 연결의 세션이 폐기되어도 다음 세션을 재검증한다.")
    void continuesSessionRevalidationWhileRevokedConnectionIsWriting() {
        SseEmitterRepository localRepository = spy(new InMemorySseEmitterRepository());
        SseDeliveryCoordinator localCoordinator = new SseDeliveryCoordinator(
                mock(SseRecoveryService.class), mock(TaskScheduler.class),
                new SseRetryProperties(Duration.ofSeconds(1), Duration.ofSeconds(30), 3),
                new SseConnectionCloser(localRepository), localRepository);
        LoginSession revokedSession = new LoginSession(UUID.randomUUID());
        LoginSession currentSession = new LoginSession(UUID.randomUUID());
        Connection slowConnection = Connection.create();
        Connection currentConnection = Connection.create();
        CountDownLatch slowSendStarted = new CountDownLatch(1);
        CountDownLatch releaseSlowSend = new CountDownLatch(1);
        CountDownLatch revokedSessionChecked = new CountDownLatch(1);
        CountDownLatch closeAttempted = new CountDownLatch(1);
        CountDownLatch currentSessionChecked = new CountDownLatch(1);

        SseEmitter slowEmitter = new SseEmitter() {
            @Override
            public void send(SseEventBuilder event) throws IOException {
                writeLock.lock();
                try {
                    slowSendStarted.countDown();
                    if (!releaseSlowSend.await(10, TimeUnit.SECONDS)) {
                        throw new IOException("느린 전송 대기 시간 초과");
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new IOException(e);
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
        SseEmitter currentEmitter = new SseEmitter();
        localCoordinator.register(receiverId, slowConnection, slowEmitter, revokedSession);
        localCoordinator.register(receiverId, currentConnection, currentEmitter, currentSession);

        Set<LoginSession> sessions = new LinkedHashSet<>(List.of(revokedSession, currentSession));
        doReturn(Map.of(receiverId, sessions)).when(localRepository).findAllLoginSessions();
        when(userSessionRepository.isCurrent(receiverId, revokedSession.id())).thenAnswer(invocation -> {
            revokedSessionChecked.countDown();
            return false;
        });
        when(userSessionRepository.isCurrent(receiverId, currentSession.id())).thenAnswer(invocation -> {
            currentSessionChecked.countDown();
            return true;
        });

        new ApplicationContextRunner()
                .withUserConfiguration(SseHeartbeatConfig.class)
                .withBean(SseEmitterRepository.class, () -> localRepository)
                .withBean(SseDeliveryCoordinator.class, () -> localCoordinator)
                .withPropertyValues("everyone.sse.heartbeat.pool-size=2", "everyone.sse.heartbeat.queue-capacity=1")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    SseHeartbeatDispatcher dispatcher = context.getBean(SseHeartbeatDispatcher.class);
                    SseDeliveryService asyncService = new SseDeliveryService(
                            localRepository, localCoordinator, userSessionRepository, dispatcher);
                    SseHeartbeatScheduler scheduler = new SseHeartbeatScheduler(asyncService);
                    ExecutorService schedulerThread = Executors.newSingleThreadExecutor();

                    try {
                        dispatcher.submit(receiverId, slowConnection);
                        assertThat(slowSendStarted.await(5, TimeUnit.SECONDS))
                                .as("느린 연결의 전송이 쓰기 잠금을 잡았다.")
                                .isTrue();

                        Future<?> tick = schedulerThread.submit(scheduler::sendHeartbeat);
                        assertThat(revokedSessionChecked.await(5, TimeUnit.SECONDS))
                                .as("폐기된 세션을 먼저 조회했다.")
                                .isTrue();
                        boolean currentSessionRevalidated = currentSessionChecked.await(2, TimeUnit.SECONDS);
                        assertThat(currentSessionRevalidated)
                                .as("느린 전송 대기 중 정상 세션을 조회했다. complete() 진입=%s",
                                        closeAttempted.getCount() == 0)
                                .isTrue();
                        tick.get(2, TimeUnit.SECONDS);
                    } finally {
                        releaseSlowSend.countDown();
                        schedulerThread.shutdown();
                        if (!schedulerThread.awaitTermination(5, TimeUnit.SECONDS)) {
                            schedulerThread.shutdownNow();
                        }
                    }

                    await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> {
                        assertThat(localRepository.findByUserIdAndConnection(receiverId, slowConnection)).isEmpty();
                        assertThat(localRepository.findByUserIdAndConnection(receiverId, currentConnection))
                                .containsSame(currentEmitter);
                    });
                });
    }
}
