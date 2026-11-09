package com.codeit.sb13.playlist_for_everyone.global.sse.service;

import com.codeit.sb13.playlist_for_everyone.global.sse.config.SseProperties;
import com.codeit.sb13.playlist_for_everyone.global.sse.event.Connection;
import com.codeit.sb13.playlist_for_everyone.global.sse.repository.SseEmitterRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.MockedConstruction;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * 연결 서비스가 조정기에 등록·정리를 위임하고 emitter에 초기 주석을 보내는지 검증한다.
 * 조정기와 저장소는 Mock으로 대체한다. 실제 등록 결과는 조정기와 저장소 테스트에서 확인한다.
 *
 * <p>MockedConstruction은 테스트 스레드에서 생성하는 SseEmitter를 해당 블록 안에서 Mock으로
 * 대체한다. ArgumentCaptor로 등록된 콜백을 꺼내 직접 실행하므로 브라우저나 타임아웃을 기다리지 않는다.
 * Mock emitter의 complete 호출만으로 실제 MVC 콜백이 실행되지는 않는다.
 * 실제 HTTP 전송과 컨테이너의 비동기 처리는 별도의 컨트롤러·HTTP 통합 테스트가 담당한다.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("SSE 연결 서비스")
class SseConnectionServiceTest {

    @Mock
    private SseEmitterRepository repository;

    @Mock
    private SseDeliveryCoordinator deliveryCoordinator;

    private SseConnectionService service;

    @BeforeEach
    void setUp() {
        SseProperties properties = new SseProperties(
                Duration.ofSeconds(15),
                Duration.ofMinutes(30)
        );
        service = new SseConnectionService(repository, properties, deliveryCoordinator);
    }

    @Test
    @DisplayName("연결을 요청하면 사용자 ID와 연결 식별자로 반환할 에미터의 등록을 요청한다.")
    void connectRequestsRegistrationOfReturnedEmitter() {
        UUID userId = UUID.randomUUID();
        ArgumentCaptor<Connection> connectionCaptor = ArgumentCaptor.forClass(Connection.class);

        SseEmitter emitter = service.connect(userId);

        assertThat(emitter).isNotNull();
        verify(deliveryCoordinator).register(eq(userId), connectionCaptor.capture(), same(emitter));
        verifyNoInteractions(repository);

        Connection connection = connectionCaptor.getValue();
        assertThat(connection).isNotNull();
        assertThat(connection.id()).isNotNull();
    }

    @Test
    @DisplayName("조정기 등록 실패 시 기존 연결의 정리·종료와 초기 전송을 요청하지 않고 원래 예외를 전달한다.")
    void registrationFailurePreservesExceptionWithoutCleanup() throws IOException {
        UUID userId = UUID.randomUUID();
        IllegalStateException failure = new IllegalStateException("테스트용 등록 실패");
        doThrow(failure).when(deliveryCoordinator).register(eq(userId), any(), any());
        try (MockedConstruction<SseEmitter> construction = mockConstruction(SseEmitter.class)) {
            assertThatThrownBy(() -> service.connect(userId)).isSameAs(failure);
            SseEmitter emitter = construction.constructed().get(0);
            ArgumentCaptor<Connection> connectionCaptor = ArgumentCaptor.forClass(Connection.class);
            verify(deliveryCoordinator).register(eq(userId), connectionCaptor.capture(), same(emitter));
            verify(deliveryCoordinator, never()).cleanupConnection(any(), any());
            verify(deliveryCoordinator, never()).closeConnection(any(), any());
            verify(deliveryCoordinator, never()).recover(any(), any(), any());
            verify(emitter, never()).send(any(SseEmitter.SseEventBuilder.class));
            verify(emitter, never()).complete();
            verifyNoInteractions(repository);
        }
    }

    @Test
    @DisplayName("연결을 요청하면 설정한 연결 제한 시간이 에미터에 적용된다.")
    void connectAppliesConfiguredTimeout() {
        Duration connectionTimeout = Duration.ofMinutes(5);
        SseProperties properties = new SseProperties(
                Duration.ofSeconds(15),
                connectionTimeout
        );
        SseConnectionService serviceWithCustomTimeout =
                new SseConnectionService(repository, properties, deliveryCoordinator);

        SseEmitter emitter = serviceWithCustomTimeout.connect(UUID.randomUUID(), null);

        assertThat(emitter.getTimeout()).isEqualTo(connectionTimeout.toMillis());
    }

    @Test
    @DisplayName("같은 사용자가 두 번 연결하면 서로 다른 연결 식별자와 에미터로 등록을 요청한다.")
    void connectTwiceRequestsRegistrationOfDistinctConnections() {
        UUID userId = UUID.randomUUID();
        ArgumentCaptor<Connection> firstConnectionCaptor = ArgumentCaptor.forClass(Connection.class);
        ArgumentCaptor<Connection> secondConnectionCaptor = ArgumentCaptor.forClass(Connection.class);

        SseEmitter firstEmitter = service.connect(userId);
        SseEmitter secondEmitter = service.connect(userId);

        assertThat(secondEmitter).isNotSameAs(firstEmitter);
        verify(deliveryCoordinator).register(eq(userId), firstConnectionCaptor.capture(), same(firstEmitter));
        verify(deliveryCoordinator).register(eq(userId), secondConnectionCaptor.capture(), same(secondEmitter));
        verify(deliveryCoordinator, times(2)).register(any(), any(), any());

        assertThat(firstConnectionCaptor.getValue().id()).isNotNull();
        assertThat(secondConnectionCaptor.getValue().id()).isNotNull()
                .isNotEqualTo(firstConnectionCaptor.getValue().id());
    }

    @Test
    @DisplayName("서로 다른 사용자가 연결하면 각 사용자 ID에 대응하는 에미터의 등록을 요청한다.")
    void connectRequestsRegistrationForEachUser() {
        UUID firstUserId = UUID.randomUUID();
        UUID secondUserId = UUID.randomUUID();
        ArgumentCaptor<Connection> firstConnectionCaptor = ArgumentCaptor.forClass(Connection.class);
        ArgumentCaptor<Connection> secondConnectionCaptor = ArgumentCaptor.forClass(Connection.class);

        SseEmitter firstEmitter = service.connect(firstUserId);
        SseEmitter secondEmitter = service.connect(secondUserId);

        verify(deliveryCoordinator).register(eq(firstUserId), firstConnectionCaptor.capture(), same(firstEmitter));
        verify(deliveryCoordinator).register(eq(secondUserId), secondConnectionCaptor.capture(), same(secondEmitter));
        verify(deliveryCoordinator, times(2)).register(any(), any(), any());

        assertThat(secondEmitter).isNotSameAs(firstEmitter);
        assertThat(firstConnectionCaptor.getValue().id()).isNotNull();
        assertThat(secondConnectionCaptor.getValue().id()).isNotNull()
                .isNotEqualTo(firstConnectionCaptor.getValue().id());
    }

    @Test
    @DisplayName("연결 완료 콜백이 실행되면 HTTP 종료를 다시 요청하지 않고 연결 정리를 조정기에 위임한다.")
    void completionCallbackDelegatesConnectionCleanup() {
        UUID userId = UUID.randomUUID();

        try (MockedConstruction<SseEmitter> ignored = mockConstruction(SseEmitter.class)) {
            SseEmitter emitter = service.connect(userId);
            ArgumentCaptor<Connection> connectionCaptor = ArgumentCaptor.forClass(Connection.class);
            verify(deliveryCoordinator).register(eq(userId), connectionCaptor.capture(), same(emitter));

            ArgumentCaptor<Runnable> completionCaptor = ArgumentCaptor.forClass(Runnable.class);
            verify(emitter).onCompletion(completionCaptor.capture());
            verify(deliveryCoordinator, never()).cleanupConnection(any(), any());

            // onCompletion은 등록 메서드다. 등록한 Runnable을 실행하여 완료 상황을 재현한다.
            completionCaptor.getValue().run();

            Connection connection = connectionCaptor.getValue();

            verify(deliveryCoordinator).cleanupConnection(eq(userId), eq(connection));
            verify(deliveryCoordinator, times(1)).cleanupConnection(any(), any());
            verify(deliveryCoordinator, never()).closeConnection(any(), any());
            verify(repository, never()).delete(any(), any());
            verify(emitter, never()).complete();
        }
    }

    @Test
    @DisplayName("같은 사용자의 연결 중 완료된 연결에 대해서만 조정기에 정리를 위임한다.")
    void completionCallbackDelegatesCleanupOnlyForCompletedConnection() {
        UUID userId = UUID.randomUUID();

        try (MockedConstruction<SseEmitter> ignored = mockConstruction(SseEmitter.class)) {
            SseEmitter firstEmitter = service.connect(userId);
            SseEmitter secondEmitter = service.connect(userId);
            ArgumentCaptor<Connection> firstConnectionCaptor = ArgumentCaptor.forClass(Connection.class);
            ArgumentCaptor<Connection> secondConnectionCaptor = ArgumentCaptor.forClass(Connection.class);
            verify(deliveryCoordinator).register(eq(userId), firstConnectionCaptor.capture(), same(firstEmitter));
            verify(deliveryCoordinator).register(eq(userId), secondConnectionCaptor.capture(), same(secondEmitter));
            assertThat(secondConnectionCaptor.getValue()).isNotEqualTo(firstConnectionCaptor.getValue());

            ArgumentCaptor<Runnable> completionCaptor = ArgumentCaptor.forClass(Runnable.class);
            verify(firstEmitter).onCompletion(completionCaptor.capture());

            completionCaptor.getValue().run();

            verify(deliveryCoordinator).cleanupConnection(eq(userId), eq(firstConnectionCaptor.getValue()));
            verify(deliveryCoordinator, times(1)).cleanupConnection(any(), any());
            verify(repository, never()).delete(any(), any());
        }
    }

    @Test
    @DisplayName("한 사용자의 연결이 완료되면 해당 사용자 연결에 대해서만 조정기에 정리를 위임한다.")
    void completionCallbackDelegatesCleanupOnlyForCompletedUser() {
        UUID firstUserId = UUID.randomUUID();
        UUID secondUserId = UUID.randomUUID();

        try (MockedConstruction<SseEmitter> ignored = mockConstruction(SseEmitter.class)) {
            SseEmitter firstEmitter = service.connect(firstUserId);
            SseEmitter secondEmitter = service.connect(secondUserId);
            ArgumentCaptor<Connection> firstConnectionCaptor = ArgumentCaptor.forClass(Connection.class);
            ArgumentCaptor<Connection> secondConnectionCaptor = ArgumentCaptor.forClass(Connection.class);
            verify(deliveryCoordinator).register(eq(firstUserId), firstConnectionCaptor.capture(), same(firstEmitter));
            verify(deliveryCoordinator).register(eq(secondUserId), secondConnectionCaptor.capture(), same(secondEmitter));

            ArgumentCaptor<Runnable> completionCaptor = ArgumentCaptor.forClass(Runnable.class);
            verify(firstEmitter).onCompletion(completionCaptor.capture());

            completionCaptor.getValue().run();

            verify(deliveryCoordinator).cleanupConnection(eq(firstUserId), eq(firstConnectionCaptor.getValue()));
            verify(deliveryCoordinator, times(1)).cleanupConnection(any(), any());
            verify(repository, never()).delete(any(), any());
        }
    }

    @Test
    @DisplayName("새 연결이 등록된 뒤 이전 연결의 완료 콜백을 다시 실행해도 이전 연결만 정리를 위임한다.")
    void repeatedCompletionCallbackDelegatesCleanupOnlyForOldConnection() {
        UUID userId = UUID.randomUUID();

        try (MockedConstruction<SseEmitter> ignored = mockConstruction(SseEmitter.class)) {
            SseEmitter oldEmitter = service.connect(userId);
            ArgumentCaptor<Connection> oldConnectionCaptor = ArgumentCaptor.forClass(Connection.class);
            verify(deliveryCoordinator).register(eq(userId), oldConnectionCaptor.capture(), same(oldEmitter));

            ArgumentCaptor<Runnable> completionCaptor = ArgumentCaptor.forClass(Runnable.class);
            verify(oldEmitter).onCompletion(completionCaptor.capture());
            Runnable oldCompletion = completionCaptor.getValue();

            oldCompletion.run();
            verify(deliveryCoordinator).cleanupConnection(eq(userId), eq(oldConnectionCaptor.getValue()));

            SseEmitter newEmitter = service.connect(userId);
            ArgumentCaptor<Connection> newConnectionCaptor = ArgumentCaptor.forClass(Connection.class);
            verify(deliveryCoordinator).register(eq(userId), newConnectionCaptor.capture(), same(newEmitter));
            assertThat(newConnectionCaptor.getValue()).isNotEqualTo(oldConnectionCaptor.getValue());

            // 이전 연결의 지연된 콜백이 다시 도착해도 새 연결 ID를 삭제 대상으로 사용하면 안 된다.
            oldCompletion.run();

            verify(deliveryCoordinator, times(2)).cleanupConnection(eq(userId), eq(oldConnectionCaptor.getValue()));
            verify(deliveryCoordinator, times(2)).cleanupConnection(any(), any());

            verify(repository, never()).delete(any(), any());
        }
    }

    @Test
    @DisplayName("타임아웃 콜백이 실행되면 연결 정리를 조정기에 위임하고 에미터 종료를 요청한다.")
    void timeoutCallbackDelegatesCleanupAndRequestsCompletion() {
        UUID userId = UUID.randomUUID();

        try (MockedConstruction<SseEmitter> ignored = mockConstruction(SseEmitter.class)) {
            SseEmitter emitter = service.connect(userId);
            ArgumentCaptor<Connection> connectionCaptor = ArgumentCaptor.forClass(Connection.class);
            verify(deliveryCoordinator).register(eq(userId), connectionCaptor.capture(), same(emitter));

            ArgumentCaptor<Runnable> timeoutCaptor = ArgumentCaptor.forClass(Runnable.class);
            verify(emitter).onTimeout(timeoutCaptor.capture());
            verify(deliveryCoordinator, never()).cleanupConnection(any(), any());

            timeoutCaptor.getValue().run();

            Connection connection = connectionCaptor.getValue();

            verify(deliveryCoordinator).cleanupConnection(eq(userId), eq(connection));
            verify(deliveryCoordinator, times(1)).cleanupConnection(any(), any());
            verify(repository, never()).delete(any(), any());
            verify(emitter).complete();
        }
    }

    @Test
    @DisplayName("같은 사용자의 연결 중 타임아웃된 연결만 정리를 위임하고 종료를 요청한다.")
    void timeoutCallbackDelegatesCleanupOnlyForTimedOutConnection() {
        UUID userId = UUID.randomUUID();

        try (MockedConstruction<SseEmitter> ignored = mockConstruction(SseEmitter.class)) {
            SseEmitter firstEmitter = service.connect(userId);
            SseEmitter secondEmitter = service.connect(userId);
            ArgumentCaptor<Connection> firstConnectionCaptor = ArgumentCaptor.forClass(Connection.class);
            ArgumentCaptor<Connection> secondConnectionCaptor = ArgumentCaptor.forClass(Connection.class);
            verify(deliveryCoordinator).register(eq(userId), firstConnectionCaptor.capture(), same(firstEmitter));
            verify(deliveryCoordinator).register(eq(userId), secondConnectionCaptor.capture(), same(secondEmitter));
            assertThat(secondConnectionCaptor.getValue()).isNotEqualTo(firstConnectionCaptor.getValue());

            ArgumentCaptor<Runnable> timeoutCaptor = ArgumentCaptor.forClass(Runnable.class);
            verify(firstEmitter).onTimeout(timeoutCaptor.capture());

            timeoutCaptor.getValue().run();

            verify(deliveryCoordinator).cleanupConnection(eq(userId), eq(firstConnectionCaptor.getValue()));
            verify(deliveryCoordinator, times(1)).cleanupConnection(any(), any());
            verify(repository, never()).delete(any(), any());
            verify(firstEmitter).complete();
            verify(secondEmitter, never()).complete();
            verify(secondEmitter, never()).completeWithError(any());
        }
    }

    @Test
    @DisplayName("타임아웃 후 이전 연결의 완료 콜백이 실행돼도 이전 연결만 정리를 위임하고 새 연결은 유지한다.")
    void completionAfterTimeoutDelegatesCleanupOnlyForOldConnection() {
        UUID userId = UUID.randomUUID();

        try (MockedConstruction<SseEmitter> ignored = mockConstruction(SseEmitter.class)) {
            SseEmitter oldEmitter = service.connect(userId);
            ArgumentCaptor<Connection> oldConnectionCaptor = ArgumentCaptor.forClass(Connection.class);
            verify(deliveryCoordinator).register(eq(userId), oldConnectionCaptor.capture(), same(oldEmitter));

            ArgumentCaptor<Runnable> timeoutCaptor = ArgumentCaptor.forClass(Runnable.class);
            ArgumentCaptor<Runnable> completionCaptor = ArgumentCaptor.forClass(Runnable.class);
            verify(oldEmitter).onTimeout(timeoutCaptor.capture());
            verify(oldEmitter).onCompletion(completionCaptor.capture());

            timeoutCaptor.getValue().run();
            verify(deliveryCoordinator).cleanupConnection(eq(userId), eq(oldConnectionCaptor.getValue()));
            verify(oldEmitter).complete();

            SseEmitter newEmitter = service.connect(userId);
            ArgumentCaptor<Connection> newConnectionCaptor = ArgumentCaptor.forClass(Connection.class);
            verify(deliveryCoordinator).register(eq(userId), newConnectionCaptor.capture(), same(newEmitter));
            assertThat(newConnectionCaptor.getValue()).isNotEqualTo(oldConnectionCaptor.getValue());

            completionCaptor.getValue().run();

            verify(deliveryCoordinator, times(2)).cleanupConnection(eq(userId), eq(oldConnectionCaptor.getValue()));
            verify(deliveryCoordinator, times(2)).cleanupConnection(any(), any());
            verify(repository, never()).delete(any(), any());
            verify(newEmitter, never()).complete();
            verify(newEmitter, never()).completeWithError(any());
        }
    }

    @Test
    @DisplayName("오류 콜백이 실행되면 해당 연결의 정리를 조정기에 위임한다.")
    void errorCallbackDelegatesCleanupOfRegisteredConnection() {
        UUID userId = UUID.randomUUID();

        try (MockedConstruction<SseEmitter> ignored = mockConstruction(SseEmitter.class)) {
            SseEmitter emitter = service.connect(userId);
            ArgumentCaptor<Connection> connectionCaptor = ArgumentCaptor.forClass(Connection.class);
            verify(deliveryCoordinator).register(eq(userId), connectionCaptor.capture(), same(emitter));

            ArgumentCaptor<Consumer<Throwable>> errorCaptor = ArgumentCaptor.captor();
            verify(emitter).onError(errorCaptor.capture());
            verify(deliveryCoordinator, never()).cleanupConnection(any(), any());

            errorCaptor.getValue().accept(new IOException("연결 오류"));

            Connection connection = connectionCaptor.getValue();

            verify(deliveryCoordinator).cleanupConnection(eq(userId), eq(connection));
            verify(deliveryCoordinator, times(1)).cleanupConnection(any(), any());
            verify(repository, never()).delete(any(), any());
        }
    }

    @Test
    @DisplayName("같은 사용자의 연결 중 오류가 발생한 연결만 정리를 위임하고 다른 연결은 종료하지 않는다.")
    void errorCallbackDelegatesCleanupOnlyForFailedConnection() {
        UUID userId = UUID.randomUUID();

        try (MockedConstruction<SseEmitter> ignored = mockConstruction(SseEmitter.class)) {
            SseEmitter firstEmitter = service.connect(userId);
            SseEmitter secondEmitter = service.connect(userId);
            ArgumentCaptor<Connection> firstConnectionCaptor = ArgumentCaptor.forClass(Connection.class);
            ArgumentCaptor<Connection> secondConnectionCaptor = ArgumentCaptor.forClass(Connection.class);
            verify(deliveryCoordinator).register(eq(userId), firstConnectionCaptor.capture(), same(firstEmitter));
            verify(deliveryCoordinator).register(eq(userId), secondConnectionCaptor.capture(), same(secondEmitter));
            assertThat(secondConnectionCaptor.getValue()).isNotEqualTo(firstConnectionCaptor.getValue());

            ArgumentCaptor<Consumer<Throwable>> errorCaptor = ArgumentCaptor.captor();
            verify(firstEmitter).onError(errorCaptor.capture());

            errorCaptor.getValue().accept(new IOException("연결 오류"));

            verify(deliveryCoordinator).cleanupConnection(eq(userId), eq(firstConnectionCaptor.getValue()));
            verify(deliveryCoordinator, times(1)).cleanupConnection(any(), any());
            verify(repository, never()).delete(any(), any());
            verify(secondEmitter, never()).complete();
            verify(secondEmitter, never()).completeWithError(any());
        }
    }

    @Test
    @DisplayName("연결을 요청하면 이벤트 ID 없는 초기 SSE 주석을 전송한다.")
    void connectSendsInitialComment() throws IOException {
        UUID userId = UUID.randomUUID();

        try (MockedConstruction<SseEmitter> ignored = mockConstruction(SseEmitter.class)) {
            SseEmitter emitter = service.connect(userId);
            ArgumentCaptor<SseEmitter.SseEventBuilder> eventCaptor =
                    ArgumentCaptor.forClass(SseEmitter.SseEventBuilder.class);

            verify(emitter).send(eventCaptor.capture());

            // 주석과 프레임 종료 구분자만 있는지 확인한다. id 필드가 생기면 이 검증이 실패한다.
            assertThat(eventCaptor.getValue().build())
                    .extracting(data -> data.getData())
                    .containsExactly(":connected\n\n");
        }
    }

    @Test
    @DisplayName("초기 주석 전송에 실패하면 연결 정리를 조정기에 위임하고 원래 예외를 보존한다.")
    void initialCommentFailureDelegatesCleanupAndPreservesCause() {
        UUID userId = UUID.randomUUID();
        IOException sendFailure = new IOException("초기 주석 전송 실패");
        ArgumentCaptor<Connection> connectionCaptor = ArgumentCaptor.forClass(Connection.class);

        // send가 호출되는 바로 그 시점에 조정기 등록이 이미 실행됐는지 확인하고 예외를 발생시킨다.
        // 실제 초기 send는 버퍼링될 수 있으므로, 여기서는 서비스가 직접 잡는 예외 처리 경로를 검증한다.
        try (MockedConstruction<SseEmitter> emitterConstruction = mockConstruction(
                SseEmitter.class,
                (emitter, context) -> doAnswer(invocation -> {
                    verify(deliveryCoordinator).register(eq(userId), connectionCaptor.capture(), same(emitter));
                    throw sendFailure;
                }).when(emitter).send(any(SseEmitter.SseEventBuilder.class))
        )) {
            assertThatThrownBy(() -> service.connect(userId))
                    .isInstanceOf(UncheckedIOException.class)
                    .cause()
                    .isSameAs(sendFailure);

            verify(deliveryCoordinator).cleanupConnection(eq(userId), eq(connectionCaptor.getValue()));
            verify(deliveryCoordinator, times(1)).cleanupConnection(any(), any());
            verify(repository, never()).delete(any(), any());
        }
    }

    @Test
    @DisplayName("전체 연결 정리를 요청하면 에미터 종료를 요청하고 연결 정리를 조정기에 위임한다.")
    void closeAllLocalConnectionsRequestsCompletionAndDelegatesCleanup(){
        UUID userId = UUID.randomUUID();
        Connection connection = Connection.create();
        SseEmitter emitter = mock(SseEmitter.class);

        when(repository.findAll()).thenReturn(
                Map.of(userId,
                        Map.of(connection, emitter)
                )
        );

        service.closeAllLocalConnections();

        verify(emitter).complete();
        verify(deliveryCoordinator).cleanupConnection(eq(userId), eq(connection));
        verify(deliveryCoordinator, times(1)).cleanupConnection(any(), any());
        verify(repository, never()).delete(any(), any());
    }

    @Test
    @DisplayName("초기 주석 전송 중 상태 예외가 발생하면 연결 정리를 조정기에 위임하고 원래 예외를 전달한다.")
    void initialCommentIllegalStateDelegatesCleanupAndRethrowsOriginalException(){
        UUID userId = UUID.randomUUID();
        IllegalStateException sendFailure = new IllegalStateException("이미 종료된 연결입니다.");

        ArgumentCaptor<Connection> connectionCaptor = ArgumentCaptor.forClass(Connection.class);

        try (MockedConstruction<SseEmitter> emitterConstruction = mockConstruction(SseEmitter.class,
                (emitter, context)-> doAnswer(invocation -> {
                    verify(deliveryCoordinator).register(eq(userId), connectionCaptor.capture(), same(emitter));
                    throw sendFailure;
                }).when(emitter).send(any(SseEmitter.SseEventBuilder.class))
        )){
            assertThatThrownBy(()-> service.connect(userId))
                    .isSameAs(sendFailure);

            verify(deliveryCoordinator).cleanupConnection(eq(userId), eq(connectionCaptor.getValue()));
            verify(deliveryCoordinator, times(1)).cleanupConnection(any(), any());
            verify(repository, never()).delete(any(), any());
        }
    }

    @Test
    @DisplayName("한 연결의 종료가 실패해도 해당 연결의 정리를 위임하고 나머지 사용자 연결의 정리를 계속한다.")
    void closeAllContinuesCleanupDelegationWhenCompletionFails(){
        UUID firstUserId = UUID.randomUUID();
        UUID secondUserId = UUID.randomUUID();

        Connection failedConnection = Connection.create();
        Connection sameUserConnection = Connection.create();
        Connection otherUserConnection = Connection.create();

        SseEmitter failedEmitter = mock(SseEmitter.class);
        SseEmitter sameUserEmitter = mock(SseEmitter.class);
        SseEmitter otherUserEmitter = mock(SseEmitter.class);

        // 실패 연결을 먼저 순회하게 만들어 실패 이후에도 나머지 연결 정리가 계속되는지 확인한다.
        Map<Connection, SseEmitter> firstUserConnections = new LinkedHashMap<>();

        firstUserConnections.put(failedConnection, failedEmitter);
        firstUserConnections.put(sameUserConnection, sameUserEmitter);

        Map<UUID, Map<Connection, SseEmitter>> connections = new LinkedHashMap<>();
        connections.put(firstUserId, firstUserConnections);
        connections.put(secondUserId, Map.of(otherUserConnection, otherUserEmitter));

        when(repository.findAll()).thenReturn(connections);
        doThrow(new IllegalStateException("연결 종료 실패"))
                .when(failedEmitter).complete();

        assertThatCode(()-> service.closeAllLocalConnections())
                .doesNotThrowAnyException();

        verify(failedEmitter).complete();
        verify(sameUserEmitter).complete();
        verify(otherUserEmitter).complete();

        verify(deliveryCoordinator).cleanupConnection(eq(firstUserId), eq(failedConnection));
        verify(deliveryCoordinator).cleanupConnection(eq(firstUserId), eq(sameUserConnection));
        verify(deliveryCoordinator).cleanupConnection(eq(secondUserId), eq(otherUserConnection));
        verify(deliveryCoordinator, times(3)).cleanupConnection(any(), any());
        verify(repository, never()).delete(any(), any());
    }

    @Test
    @DisplayName("조정기에 연결 등록을 위임한 뒤 마지막 이벤트 ID로 복구를 요청한다.")
    void connectDelegatesRegistrationBeforeRequestingRecovery(){
        UUID userId = UUID.randomUUID();
        UUID lastEventId = UUID.randomUUID();

        ArgumentCaptor<Connection> connectionCaptor = ArgumentCaptor.forClass(Connection.class);
        SseEmitter emitter = service.connect(userId, lastEventId);

        InOrder inOrder = inOrder(deliveryCoordinator);
        inOrder.verify(deliveryCoordinator)
                        .register(eq(userId), connectionCaptor.capture(), same(emitter));

        Connection connection = connectionCaptor.getValue();

        inOrder.verify(deliveryCoordinator)
                .recover(userId, connection, lastEventId);
    }

    @Test
    @DisplayName("마지막 이벤트 ID가 없어도 등록한 연결의 복구를 요청한다.")
    void connectRequestsCoordinatorRecoveryWithoutLastEventId(){
        UUID userId = UUID.randomUUID();

        ArgumentCaptor<Connection> connectionCaptor = ArgumentCaptor.forClass(Connection.class);

        SseEmitter emitter = service.connect(userId);

        verify(deliveryCoordinator)
                .register(eq(userId), connectionCaptor.capture(), same(emitter));

        verify(deliveryCoordinator)
                .recover(eq(userId), eq(connectionCaptor.getValue()), isNull());
    }

    @Test
    @DisplayName("복구 이력 조회에 실패하면 등록된 연결의 정리를 위임하고 원래 예외를 전달한다.")
    void recoveryLookupFailureDelegatesRegisteredConnectionCleanup(){
        UUID userId = UUID.randomUUID();
        UUID lastEventId = UUID.randomUUID();

        DataAccessResourceFailureException failure = new DataAccessResourceFailureException("테스트용 이력 조회 실패");

        ArgumentCaptor<Connection> connectionCaptor = ArgumentCaptor.forClass(Connection.class);
        ArgumentCaptor<SseEmitter> emitterCaptor = ArgumentCaptor.forClass(SseEmitter.class);

        doThrow(failure)
                .when(deliveryCoordinator)
                .recover(eq(userId), any(Connection.class), eq(lastEventId));

        assertThatThrownBy(() -> service.connect(userId, lastEventId))
                .isSameAs(failure);

        verify(deliveryCoordinator)
                .register(eq(userId), connectionCaptor.capture(), emitterCaptor.capture());

        verify(deliveryCoordinator).cleanupConnection(eq(userId), eq(connectionCaptor.getValue()));
        verify(deliveryCoordinator, times(1)).cleanupConnection(any(), any());
        verify(repository, never()).delete(any(), any());
    }

    @Test
    @DisplayName("복구 전송 중 I/O 오류가 발생하면 등록된 연결의 정리를 위임하고 원래 예외를 전달한다.")
    void recoveryIoFailureDelegatesRegisteredConnectionCleanup(){
        UUID userId = UUID.randomUUID();
        UUID lastEventId = UUID.randomUUID();

        UncheckedIOException failure = new UncheckedIOException("SSE 복구 이벤트 전송에 실패했습니다.", new IOException("테스트용 복구 전송 실패"));

        ArgumentCaptor<Connection> connectionCaptor = ArgumentCaptor.forClass(Connection.class);
        ArgumentCaptor<SseEmitter> emitterCaptor = ArgumentCaptor.forClass(SseEmitter.class);

        doThrow(failure)
                .when(deliveryCoordinator)
                .recover(
                        eq(userId),
                        any(Connection.class),
                        eq(lastEventId)
                );

        assertThatThrownBy(()-> service.connect(userId, lastEventId))
                .isSameAs(failure);

        verify(deliveryCoordinator).register(eq(userId), connectionCaptor.capture(), emitterCaptor.capture());
        verify(deliveryCoordinator).cleanupConnection(eq(userId), eq(connectionCaptor.getValue()));
        verify(deliveryCoordinator, times(1)).cleanupConnection(any(), any());
        verify(repository, never()).delete(any(), any());
    }

    @Test
    @DisplayName("복구 전송 중 상태 예외가 발생하면 등록된 연결의 정리를 위임하고 원래 예외를 전달한다.")
    void recoveryIllegalStateFailureDelegatesRegisteredConnectionCleanup(){
        UUID userId = UUID.randomUUID();
        UUID lastEventId = UUID.randomUUID();

        IllegalStateException failure = new IllegalStateException("테스트용 복구 전송 상태 오류");
        ArgumentCaptor<Connection> connectionCaptor = ArgumentCaptor.forClass(Connection.class);
        ArgumentCaptor<SseEmitter> emitterCaptor = ArgumentCaptor.forClass(SseEmitter.class);

        doThrow(failure)
                .when(deliveryCoordinator)
                .recover(
                        eq(userId),
                        any(Connection.class),
                        eq(lastEventId)
                );

        assertThatThrownBy(() -> service.connect(userId, lastEventId))
                .isSameAs(failure);

        verify(deliveryCoordinator).register(eq(userId), connectionCaptor.capture(), emitterCaptor.capture());
        verify(deliveryCoordinator).cleanupConnection(eq(userId), eq(connectionCaptor.getValue()));
        verify(deliveryCoordinator, times(1)).cleanupConnection(any(), any());
        verify(repository, never()).delete(any(), any());
    }
}
