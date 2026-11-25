package com.codeit.sb13.playlist_for_everyone.global.sse.service;

import com.codeit.sb13.playlist_for_everyone.domain.auth.repository.UserSessionRepository;
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
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * 전송 서비스가 수신자의 연결을 선택하고, 일부 실패에도 나머지 전송을 계속하는지 검증한다.
 * 전송 테스트는 저장소와 emitter를 Mock으로 대체하고, 서비스 내부 JSON 변환에는 실제 JsonMapper를 사용한다.
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
        service = new SseDeliveryService(repository, deliveryCoordinator, userSessionRepository);
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
        when(repository.findAll())
                .thenReturn(Map.of(userId, Map.of(connection, emitter)));

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

        when(repository.findAll())
                .thenReturn(Map.of(
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

        when(repository.findAll()).thenReturn(connectionsByUser);

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

        when(repository.findAll()).thenReturn(connectionsByUser);

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
        SseDeliveryService localService =
                new SseDeliveryService(localRepository, deliveryCoordinator, userSessionRepository);
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
        verify(deliveryCoordinator).closeConnection(userId, firstTab);
        verify(deliveryCoordinator).closeConnection(userId, secondTab);
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

        when(repository.findAll()).thenReturn(Map.of(
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
        verify(deliveryCoordinator).closeConnection(receiverId, connection);
        verifyNoMoreInteractions(deliveryCoordinator);
    }
}
