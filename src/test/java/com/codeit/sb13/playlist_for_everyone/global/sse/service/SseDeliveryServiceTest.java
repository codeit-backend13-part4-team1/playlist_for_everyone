package com.codeit.sb13.playlist_for_everyone.global.sse.service;

import com.codeit.sb13.playlist_for_everyone.global.sse.event.Connection;
import com.codeit.sb13.playlist_for_everyone.global.sse.event.Event;
import com.codeit.sb13.playlist_for_everyone.global.sse.event.SseEvent;
import com.codeit.sb13.playlist_for_everyone.global.sse.repository.SseEmitterRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("SSE 로컬 전송 서비스")
class SseDeliveryServiceTest {

    @Mock
    private SseEmitterRepository repository;

    private SseDeliveryService service;

    @BeforeEach
    void setUp() {
        service = new SseDeliveryService(repository);
    }

    @Test
    @DisplayName("수신자의 연결을 조회하여 SSE 이벤트를 전송한다.")
    void sendEventToReceiverConnection() throws IOException {
        UUID receiverId = UUID.randomUUID();
        SseEmitter emitter = mock(SseEmitter.class);
        when(repository.findAllByUserId(receiverId))
                .thenReturn(Map.of(Connection.create(), emitter));

        SseEvent<Map<String, String>> event = new SseEvent<>(
                UUID.randomUUID(),
                receiverId,
                Event.NOTIFICATIONS.getName(),
                Map.of("message", "테스트 알림")
        );

        service.sendToLocalConnections(event);

        verify(repository).findAllByUserId(receiverId);
        verify(emitter).send(any(SseEmitter.SseEventBuilder.class));
    }

    @Test
    @DisplayName("전송하는 SSE 이벤트에 이벤트 ID와 이름 및 데이터가 포함된다.")
    void sendEventPreservesIdNameAndData() throws IOException {
        UUID receiverId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();
        Connection connection = Connection.create();
        String eventName = Event.NOTIFICATIONS.getName();
        Map<String, String> payload = Map.of("message", "테스트 알림");

        SseEmitter emitter = mock(SseEmitter.class);

        when(repository.findAllByUserId(receiverId))
                .thenReturn(Map.of(connection, emitter));

        SseEvent<Map<String, String>> event = new SseEvent<>(eventId, receiverId, eventName, payload);

        service.sendToLocalConnections(event);

        ArgumentCaptor<SseEmitter.SseEventBuilder> evetCaptor = ArgumentCaptor.forClass(SseEmitter.SseEventBuilder.class);

        verify(emitter).send(evetCaptor.capture());

        assertThat(evetCaptor.getValue().build())
                .extracting(part -> part.getData())
                .containsExactly("id:" + eventId +
                        "\nevent:" + eventName +
                        "\ndata:",payload, "\n\n");

    }

    @Test
    @DisplayName("수신자의 연결이 여러 개이면 모든 연결에 SSE 이벤트를 전송한다.")
    void sendEventToAllReceiverConnections() throws IOException {
        UUID receiverId = UUID.randomUUID();

        Connection firstConnection = Connection.create();
        Connection secondConnection = Connection.create();

        SseEmitter firstEmitter = mock(SseEmitter.class);
        SseEmitter secondEmitter = mock(SseEmitter.class);

        when(repository.findAllByUserId(receiverId)).thenReturn(Map.of(
                firstConnection, firstEmitter,
                secondConnection, secondEmitter
        ));

        SseEvent<Map<String, String>> event = new SseEvent<>(
                UUID.randomUUID(),
                receiverId,
                Event.NOTIFICATIONS.getName(),
                Map.of("message", "테스트 알림")
        );

        service.sendToLocalConnections(event);

        verify(firstEmitter).send(any(SseEmitter.SseEventBuilder.class));
        verify(secondEmitter).send(any(SseEmitter.SseEventBuilder.class));
    }

    @Test
    @DisplayName("수신자의 연결이 없으면 예외나 연결 삭제 요청 없이 종료한다.")
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
        verify(repository, never()).delete(any(),any());
    }

    @Test
    @DisplayName("전송 중 IOException이 발생하면 실패한 연결만 삭제하고 다음 연결에 계속 전송한다.")
    void ioFailureDeletesOnlyFailedConnectionAndContinuesSending() throws IOException {

        UUID receiverId = UUID.randomUUID();

        Connection failedConnection = Connection.create();
        Connection healthyConnection = Connection.create();

        SseEmitter failedEmitter = mock(SseEmitter.class);
        SseEmitter healthyEmitter = mock(SseEmitter.class);

        Map<Connection, SseEmitter> connections = new LinkedHashMap<>();
        connections.put(failedConnection, failedEmitter);
        connections.put(healthyConnection, healthyEmitter);

        when(repository.findAllByUserId(receiverId)).thenReturn(connections);

        doThrow(new IOException("전송 실패"))
                .when(failedEmitter)
                .send(any(SseEmitter.SseEventBuilder.class));

        SseEvent<Map<String, String>> event = new SseEvent<>(
                UUID.randomUUID(),
                receiverId,
                Event.NOTIFICATIONS.getName(),
                Map.of("message", "테스트 알림")
        );

        assertThatCode(() -> service.sendToLocalConnections(event))
                .doesNotThrowAnyException();

        verify(failedEmitter).send(any(SseEmitter.SseEventBuilder.class));
        verify(repository).delete(receiverId, failedConnection);
        verify(repository, times(1)).delete(any(), any());

        verify(healthyEmitter).send(any(SseEmitter.SseEventBuilder.class));
    }

    @Test
    @DisplayName("전송 중 IllegalStateException 발생하면 실패한 연결만 삭제하고 다음 연결에 계속 전송한다.")
    void illegalStateFailureDeletesOnlyFailedConnectionAndContinuesSending() throws IOException {

        UUID receiverId = UUID.randomUUID();

        Connection failedConnection = Connection.create();
        Connection healthyConnection = Connection.create();

        SseEmitter failedEmitter = mock(SseEmitter.class);
        SseEmitter healthyEmitter = mock(SseEmitter.class);

        Map<Connection, SseEmitter> connections = new LinkedHashMap<>();
        connections.put(failedConnection, failedEmitter);
        connections.put(healthyConnection, healthyEmitter);

        when(repository.findAllByUserId(receiverId)).thenReturn(connections);

        doThrow(new IllegalStateException("이미 종료된 연결"))
                .when(failedEmitter)
                .send(any(SseEmitter.SseEventBuilder.class));

        SseEvent<Map<String, String>> event = new SseEvent<>(
                UUID.randomUUID(),
                receiverId,
                Event.NOTIFICATIONS.getName(),
                Map.of("message", "테스트 알림")
        );

        assertThatCode(() -> service.sendToLocalConnections(event))
                .doesNotThrowAnyException();

        verify(failedEmitter).send(any(SseEmitter.SseEventBuilder.class));
        verify(repository).delete(receiverId, failedConnection);
        verify(repository, times(1)).delete(any(), any());

        verify(healthyEmitter).send(any(SseEmitter.SseEventBuilder.class));
    }

}
