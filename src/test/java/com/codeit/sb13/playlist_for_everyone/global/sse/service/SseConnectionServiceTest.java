package com.codeit.sb13.playlist_for_everyone.global.sse.service;

import com.codeit.sb13.playlist_for_everyone.global.sse.config.SseProperties;
import com.codeit.sb13.playlist_for_everyone.global.sse.event.Connection;
import com.codeit.sb13.playlist_for_everyone.global.sse.repository.SseEmitterRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockedConstruction;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("SSE 연결 서비스")
class SseConnectionServiceTest {

    @Mock
    private SseEmitterRepository repository;

    private SseConnectionService service;

    @BeforeEach
    void setUp() {
        SseProperties properties = new SseProperties(
                Duration.ofSeconds(15),
                Duration.ofMinutes(30)
        );
        service = new SseConnectionService(repository, properties);
    }

    @Test
    @DisplayName("연결을 요청하면 사용자 ID와 연결 식별자로 반환할 에미터의 등록을 요청한다.")
    void connectRequestsRegistrationOfReturnedEmitter() {
        UUID userId = UUID.randomUUID();
        ArgumentCaptor<Connection> connectionCaptor = ArgumentCaptor.forClass(Connection.class);

        SseEmitter emitter = service.connect(userId);

        assertThat(emitter).isNotNull();
        verify(repository).save(eq(userId), connectionCaptor.capture(), same(emitter));

        Connection connection = connectionCaptor.getValue();
        assertThat(connection).isNotNull();
        assertThat(connection.id()).isNotNull();
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
                new SseConnectionService(repository, properties);

        SseEmitter emitter = serviceWithCustomTimeout.connect(UUID.randomUUID());

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
        verify(repository).save(eq(userId), firstConnectionCaptor.capture(), same(firstEmitter));
        verify(repository).save(eq(userId), secondConnectionCaptor.capture(), same(secondEmitter));
        verify(repository, times(2)).save(any(), any(), any());

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

        verify(repository).save(eq(firstUserId), firstConnectionCaptor.capture(), same(firstEmitter));
        verify(repository).save(eq(secondUserId), secondConnectionCaptor.capture(), same(secondEmitter));
        verify(repository, times(2)).save(any(), any(), any());

        assertThat(secondEmitter).isNotSameAs(firstEmitter);
        assertThat(firstConnectionCaptor.getValue().id()).isNotNull();
        assertThat(secondConnectionCaptor.getValue().id()).isNotNull()
                .isNotEqualTo(firstConnectionCaptor.getValue().id());
    }

    @Test
    @DisplayName("연결 완료 콜백이 실행되면 등록 당시의 사용자와 연결 식별자로 삭제를 요청한다.")
    void completionCallbackRequestsDeletionOfRegisteredConnection() {
        UUID userId = UUID.randomUUID();

        try (MockedConstruction<SseEmitter> ignored = mockConstruction(SseEmitter.class)) {
            SseEmitter emitter = service.connect(userId);
            ArgumentCaptor<Connection> connectionCaptor = ArgumentCaptor.forClass(Connection.class);
            verify(repository).save(eq(userId), connectionCaptor.capture(), same(emitter));

            ArgumentCaptor<Runnable> completionCaptor = ArgumentCaptor.forClass(Runnable.class);
            verify(emitter).onCompletion(completionCaptor.capture());

            completionCaptor.getValue().run();

            verify(repository).delete(userId, connectionCaptor.getValue());
            verify(repository, times(1)).delete(any(), any());
        }
    }

    @Test
    @DisplayName("같은 사용자의 연결 중 완료된 연결에 대해서만 삭제를 요청한다.")
    void completionCallbackRequestsDeletionOnlyForCompletedConnection() {
        UUID userId = UUID.randomUUID();

        try (MockedConstruction<SseEmitter> ignored = mockConstruction(SseEmitter.class)) {
            SseEmitter firstEmitter = service.connect(userId);
            SseEmitter secondEmitter = service.connect(userId);
            ArgumentCaptor<Connection> firstConnectionCaptor = ArgumentCaptor.forClass(Connection.class);
            ArgumentCaptor<Connection> secondConnectionCaptor = ArgumentCaptor.forClass(Connection.class);
            verify(repository).save(eq(userId), firstConnectionCaptor.capture(), same(firstEmitter));
            verify(repository).save(eq(userId), secondConnectionCaptor.capture(), same(secondEmitter));
            assertThat(secondConnectionCaptor.getValue()).isNotEqualTo(firstConnectionCaptor.getValue());

            ArgumentCaptor<Runnable> completionCaptor = ArgumentCaptor.forClass(Runnable.class);
            verify(firstEmitter).onCompletion(completionCaptor.capture());

            completionCaptor.getValue().run();

            verify(repository).delete(userId, firstConnectionCaptor.getValue());
            verify(repository, times(1)).delete(any(), any());
        }
    }

    @Test
    @DisplayName("한 사용자의 연결이 완료되면 해당 사용자 연결에 대해서만 삭제를 요청한다.")
    void completionCallbackRequestsDeletionOnlyForCompletedUser() {
        UUID firstUserId = UUID.randomUUID();
        UUID secondUserId = UUID.randomUUID();

        try (MockedConstruction<SseEmitter> ignored = mockConstruction(SseEmitter.class)) {
            SseEmitter firstEmitter = service.connect(firstUserId);
            SseEmitter secondEmitter = service.connect(secondUserId);
            ArgumentCaptor<Connection> firstConnectionCaptor = ArgumentCaptor.forClass(Connection.class);
            ArgumentCaptor<Connection> secondConnectionCaptor = ArgumentCaptor.forClass(Connection.class);
            verify(repository).save(eq(firstUserId), firstConnectionCaptor.capture(), same(firstEmitter));
            verify(repository).save(eq(secondUserId), secondConnectionCaptor.capture(), same(secondEmitter));

            ArgumentCaptor<Runnable> completionCaptor = ArgumentCaptor.forClass(Runnable.class);
            verify(firstEmitter).onCompletion(completionCaptor.capture());

            completionCaptor.getValue().run();

            verify(repository).delete(firstUserId, firstConnectionCaptor.getValue());
            verify(repository, times(1)).delete(any(), any());
        }
    }

    @Test
    @DisplayName("새 연결이 등록된 뒤 이전 연결의 완료 콜백을 다시 실행해도 이전 연결만 삭제 요청한다.")
    void repeatedCompletionCallbackRequestsDeletionOnlyForOldConnection() {
        UUID userId = UUID.randomUUID();

        try (MockedConstruction<SseEmitter> ignored = mockConstruction(SseEmitter.class)) {
            SseEmitter oldEmitter = service.connect(userId);
            ArgumentCaptor<Connection> oldConnectionCaptor = ArgumentCaptor.forClass(Connection.class);
            verify(repository).save(eq(userId), oldConnectionCaptor.capture(), same(oldEmitter));

            ArgumentCaptor<Runnable> completionCaptor = ArgumentCaptor.forClass(Runnable.class);
            verify(oldEmitter).onCompletion(completionCaptor.capture());
            Runnable oldCompletion = completionCaptor.getValue();

            oldCompletion.run();
            verify(repository).delete(userId, oldConnectionCaptor.getValue());

            SseEmitter newEmitter = service.connect(userId);
            ArgumentCaptor<Connection> newConnectionCaptor = ArgumentCaptor.forClass(Connection.class);
            verify(repository).save(eq(userId), newConnectionCaptor.capture(), same(newEmitter));
            assertThat(newConnectionCaptor.getValue()).isNotEqualTo(oldConnectionCaptor.getValue());

            oldCompletion.run();

            verify(repository, times(2)).delete(userId, oldConnectionCaptor.getValue());
            verify(repository, times(2)).delete(any(), any());
        }
    }

    @Test
    @DisplayName("연결 시간이 초과되면 해당 연결의 삭제와 에미터 종료를 요청한다.")
    void timeoutCallbackRequestsDeletionAndCompletion() {
        UUID userId = UUID.randomUUID();

        try (MockedConstruction<SseEmitter> ignored = mockConstruction(SseEmitter.class)) {
            SseEmitter emitter = service.connect(userId);
            ArgumentCaptor<Connection> connectionCaptor = ArgumentCaptor.forClass(Connection.class);
            verify(repository).save(eq(userId), connectionCaptor.capture(), same(emitter));

            ArgumentCaptor<Runnable> timeoutCaptor = ArgumentCaptor.forClass(Runnable.class);
            verify(emitter).onTimeout(timeoutCaptor.capture());

            timeoutCaptor.getValue().run();

            verify(repository).delete(userId, connectionCaptor.getValue());
            verify(repository, times(1)).delete(any(), any());
            verify(emitter).complete();
        }
    }

    @Test
    @DisplayName("같은 사용자의 연결 중 타임아웃된 연결에 대해서만 삭제와 종료를 요청한다.")
    void timeoutCallbackRequestsCleanupOnlyForTimedOutConnection() {
        UUID userId = UUID.randomUUID();

        try (MockedConstruction<SseEmitter> ignored = mockConstruction(SseEmitter.class)) {
            SseEmitter firstEmitter = service.connect(userId);
            SseEmitter secondEmitter = service.connect(userId);
            ArgumentCaptor<Connection> firstConnectionCaptor = ArgumentCaptor.forClass(Connection.class);
            ArgumentCaptor<Connection> secondConnectionCaptor = ArgumentCaptor.forClass(Connection.class);
            verify(repository).save(eq(userId), firstConnectionCaptor.capture(), same(firstEmitter));
            verify(repository).save(eq(userId), secondConnectionCaptor.capture(), same(secondEmitter));
            assertThat(secondConnectionCaptor.getValue()).isNotEqualTo(firstConnectionCaptor.getValue());

            ArgumentCaptor<Runnable> timeoutCaptor = ArgumentCaptor.forClass(Runnable.class);
            verify(firstEmitter).onTimeout(timeoutCaptor.capture());

            timeoutCaptor.getValue().run();

            verify(repository).delete(userId, firstConnectionCaptor.getValue());
            verify(repository, times(1)).delete(any(), any());
            verify(firstEmitter).complete();
            verify(secondEmitter, never()).complete();
            verify(secondEmitter, never()).completeWithError(any());
        }
    }

    @Test
    @DisplayName("타임아웃 후 이전 연결의 완료 콜백이 실행돼도 새 연결의 삭제나 종료를 요청하지 않는다.")
    void completionAfterTimeoutRequestsCleanupOnlyForOldConnection() {
        UUID userId = UUID.randomUUID();

        try (MockedConstruction<SseEmitter> ignored = mockConstruction(SseEmitter.class)) {
            SseEmitter oldEmitter = service.connect(userId);
            ArgumentCaptor<Connection> oldConnectionCaptor = ArgumentCaptor.forClass(Connection.class);
            verify(repository).save(eq(userId), oldConnectionCaptor.capture(), same(oldEmitter));

            ArgumentCaptor<Runnable> timeoutCaptor = ArgumentCaptor.forClass(Runnable.class);
            ArgumentCaptor<Runnable> completionCaptor = ArgumentCaptor.forClass(Runnable.class);
            verify(oldEmitter).onTimeout(timeoutCaptor.capture());
            verify(oldEmitter).onCompletion(completionCaptor.capture());

            timeoutCaptor.getValue().run();
            verify(repository).delete(userId, oldConnectionCaptor.getValue());
            verify(oldEmitter).complete();

            SseEmitter newEmitter = service.connect(userId);
            ArgumentCaptor<Connection> newConnectionCaptor = ArgumentCaptor.forClass(Connection.class);
            verify(repository).save(eq(userId), newConnectionCaptor.capture(), same(newEmitter));
            assertThat(newConnectionCaptor.getValue()).isNotEqualTo(oldConnectionCaptor.getValue());

            completionCaptor.getValue().run();

            verify(repository, times(2)).delete(userId, oldConnectionCaptor.getValue());
            verify(repository, times(2)).delete(any(), any());
            verify(newEmitter, never()).complete();
            verify(newEmitter, never()).completeWithError(any());
        }
    }

    @Test
    @DisplayName("연결 오류 콜백이 실행되면 등록 당시의 사용자와 연결 식별자로 삭제를 요청한다.")
    void errorCallbackRequestsDeletionOfRegisteredConnection() {
        UUID userId = UUID.randomUUID();

        try (MockedConstruction<SseEmitter> ignored = mockConstruction(SseEmitter.class)) {
            SseEmitter emitter = service.connect(userId);
            ArgumentCaptor<Connection> connectionCaptor = ArgumentCaptor.forClass(Connection.class);
            verify(repository).save(eq(userId), connectionCaptor.capture(), same(emitter));

            ArgumentCaptor<Consumer<Throwable>> errorCaptor = ArgumentCaptor.captor();
            verify(emitter).onError(errorCaptor.capture());

            errorCaptor.getValue().accept(new IOException("연결 오류"));

            verify(repository).delete(userId, connectionCaptor.getValue());
            verify(repository, times(1)).delete(any(), any());
        }
    }

    @Test
    @DisplayName("같은 사용자의 연결 중 오류가 발생한 연결에 대해서만 삭제를 요청하고 다른 연결은 종료하지 않는다.")
    void errorCallbackRequestsCleanupOnlyForFailedConnection() {
        UUID userId = UUID.randomUUID();

        try (MockedConstruction<SseEmitter> ignored = mockConstruction(SseEmitter.class)) {
            SseEmitter firstEmitter = service.connect(userId);
            SseEmitter secondEmitter = service.connect(userId);
            ArgumentCaptor<Connection> firstConnectionCaptor = ArgumentCaptor.forClass(Connection.class);
            ArgumentCaptor<Connection> secondConnectionCaptor = ArgumentCaptor.forClass(Connection.class);
            verify(repository).save(eq(userId), firstConnectionCaptor.capture(), same(firstEmitter));
            verify(repository).save(eq(userId), secondConnectionCaptor.capture(), same(secondEmitter));
            assertThat(secondConnectionCaptor.getValue()).isNotEqualTo(firstConnectionCaptor.getValue());

            ArgumentCaptor<Consumer<Throwable>> errorCaptor = ArgumentCaptor.captor();
            verify(firstEmitter).onError(errorCaptor.capture());

            errorCaptor.getValue().accept(new IOException("연결 오류"));

            verify(repository).delete(userId, firstConnectionCaptor.getValue());
            verify(repository, times(1)).delete(any(), any());
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

            assertThat(eventCaptor.getValue().build())
                    .extracting(data -> data.getData())
                    .containsExactly(":connected\n\n");
        }
    }

    @Test
    @DisplayName("초기 주석은 등록 요청 후 전송하며 실패하면 동일 연결의 삭제를 요청하고 원본 예외를 보존한다.")
    void initialCommentFailureRequestsDeletionAndPreservesCause() {
        UUID userId = UUID.randomUUID();
        IOException sendFailure = new IOException("초기 주석 전송 실패");
        ArgumentCaptor<Connection> connectionCaptor = ArgumentCaptor.forClass(Connection.class);

        try (MockedConstruction<SseEmitter> ignored = mockConstruction(
                SseEmitter.class,
                (emitter, context) -> doAnswer(invocation -> {
                    verify(repository).save(eq(userId), connectionCaptor.capture(), same(emitter));
                    throw sendFailure;
                }).when(emitter).send(any(SseEmitter.SseEventBuilder.class))
        )) {
            assertThatThrownBy(() -> service.connect(userId))
                    .isInstanceOf(UncheckedIOException.class)
                    .cause()
                    .isSameAs(sendFailure);

            verify(repository).delete(userId, connectionCaptor.getValue());
            verify(repository, times(1)).delete(any(), any());
        }
    }

    @Test
    @DisplayName("전체 연결 정리를 요청하면 등록된 에미터의 종료와 연결 삭제를 요청한다.")
    void closeAllLocalConnectionsRequestsCompletionAndDeletion(){
        UUID userId = UUID.randomUUID();
        Connection connection = Connection.create();
        SseEmitter emitter = mock(SseEmitter.class);

        when(repository.findAll()).thenReturn(
                Map.of(userId,
                        Map.of(connection, emitter)
                )
        );

        service.closeAllLocalConnections();

        verify(emitter).complete();;
        verify(repository).delete(userId, connection);
    }
}
