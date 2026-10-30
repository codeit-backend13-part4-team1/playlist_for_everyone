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
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * 전송 서비스가 수신자의 연결을 선택하고, 일부 실패에도 나머지 전송을 계속하는지 검증한다.
 * 저장소와 emitter는 Mock으로 대체하고, 서비스 내부 JSON 변환에는 실제 JsonMapper를 사용한다.
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

    private SseDeliveryService service;

    @BeforeEach
    void setUp() {
        service = new SseDeliveryService(repository, JsonMapper.builder().build());
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

        List<Object> parts = evetCaptor.getValue().build().stream()
                .map(part -> part.getData())
                .toList();

        assertThat(parts).hasSize(3);

        assertThat(parts.get(0))
                .isEqualTo("id:" + eventId
                + "\nevent:" +eventName
                + "\ndata:");
        assertThat(parts.get(2)).isEqualTo("\n\n");
        assertThat(parts.get(1)).isInstanceOf(String.class);

        JsonMapper jsonMapper = JsonMapper.builder().build();

        // 서비스가 준비한 JSON의 구조와 값을 비교한다. 실제 HTTP 응답은 통합 테스트에서 확인한다.
        assertThat(jsonMapper.readTree((String) parts.get(1)))
                .isEqualTo(jsonMapper.valueToTree(payload));

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

        // 순서를 고정해야 정상 연결이 우연히 먼저 처리된 결과를 '실패 후 계속 전송'으로 오해하지 않는다.
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
        verify(repository, never()).delete(any(),any());
    }

    @Test
    @DisplayName("하트비트 전송 중 IOException이 발생하면 실패한 연결만 삭제하고 다른 연결에도 계속 전송한다.")
    void heartbeatIoFailureDeletesOnlyFailedConnectionAndContinuesSending() throws IOException {
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
        verify(repository).delete(firstUserId, failedConnection);
        verify(repository, times(1)).delete(any(), any());

        verify(healthyEmitter).send(any(SseEmitter.SseEventBuilder.class));
        verify(otherUserEmitter).send(any(SseEmitter.SseEventBuilder.class));

    }

    @Test
    @DisplayName("하트비트 전송 중 IllegalStateException이 발생하면 실패한 연결만 삭제하고 다른 연결에도 계속 전송한다.")
    void heartbeatIllegalStateFailureDeletesOnlyFailedConnectionAndContinuesSending() throws IOException {
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
        verify(repository).delete(firstUserId, failedConnection);
        verify(repository, times(1)).delete(any(), any());

        verify(healthyEmitter).send(any(SseEmitter.SseEventBuilder.class));
        verify(otherUserEmitter).send(any(SseEmitter.SseEventBuilder.class));

    }
}
