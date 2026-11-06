package com.codeit.sb13.playlist_for_everyone.global.sse.service;

import com.codeit.sb13.playlist_for_everyone.global.sse.event.Connection;
import com.codeit.sb13.playlist_for_everyone.global.sse.event.Event;
import com.codeit.sb13.playlist_for_everyone.global.sse.event.SseEvent;
import com.codeit.sb13.playlist_for_everyone.global.sse.history.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.http.MediaType;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyEmitter;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("SSE 재연결 복구 서비스")
class SseRecoveryServiceTest {

    @Mock
    private SseHistoryStore historyStore;

    @Mock
    private SseEmitter emitter;

    private SseRecoveryService service;
    private final UUID userId = UUID.randomUUID();
    private final UUID lastEventId = UUID.randomUUID();
    private final Connection connection = Connection.create();
    private final JsonMapper jsonMapper = JsonMapper.builder().build();

    @BeforeEach
    void setUp(){
        service = new SseRecoveryService(historyStore);
    }

    @Test
    @DisplayName("복구를 요청하면 사용자 ID와 마지막 이벤트 ID로 스냅샷을 조회한다.")
    void requestSnapshotForUserAndLastEventId(){
        when(historyStore.snapshot(userId,lastEventId))
                .thenReturn(new SseHistorySnapshot(null, List.of()));

        service.recover(userId, connection, emitter, lastEventId);

        verify(historyStore).snapshot(userId, lastEventId);
    }

    @Test
    @DisplayName("마지막 이벤트 ID가 없으면 재전송 없이 현재 이력의 끝 위치를 반환한다.")
    void queriesSnapshotWithoutReplayWhenLastEventIdIsMissing() throws IOException {
        SseHistoryPosition upperBound = createHistoryPosition();
        SseHistorySnapshot snapshot = new SseHistorySnapshot(upperBound, List.of());

        when(historyStore.snapshot(userId, null))
                .thenReturn(snapshot);

        SseHistoryPosition result = service.recover(userId, connection, emitter, null);

        assertThat(result).isEqualTo(upperBound);
        verify(historyStore).snapshot(userId, null);
        verify(emitter, never()).send(any(SseEmitter.SseEventBuilder.class));
    }


    @Test
    @DisplayName("스냅샷 이벤트의 순서와 내용을 유지하여 전송하고 끝 위치를 반환한다.")
    void replaysSnapshotEventsInOrderAndPreservesContents() throws IOException {
        SseEvent<JsonNode> firstEvent = SseEvent.createNotificationEvent(
                UUID.fromString("00000000-0000-0000-0000-000000000002"),
                userId,
                jsonMapper.readTree("""
                        {
                            "message": "복구할 알림",
                            "metadata": {"count": 2}
                        }
                        """)
        );

        SseEvent<JsonNode> secondEvent = new SseEvent<>(
                UUID.fromString("00000000-0000-0000-0000-000000000001"),
                userId,
                Event.DIRECT_MESSAGES.getName(),
                jsonMapper.readTree("null")
        );

        SseHistoryEntry firstEntry = new SseHistoryEntry(createHistoryPosition(), firstEvent);
        SseHistoryEntry secondEntry = new SseHistoryEntry(createHistoryPosition("1000-1"), secondEvent);

        when(historyStore.snapshot(userId, lastEventId))
                .thenReturn(new SseHistorySnapshot(secondEntry.position(), List.of(firstEntry, secondEntry)));

        SseHistoryPosition result = service.recover(userId, connection, emitter, lastEventId);

        ArgumentCaptor<SseEmitter.SseEventBuilder> eventCaptor = ArgumentCaptor.forClass(SseEmitter.SseEventBuilder.class);
        verify(emitter, times(2)).send(eventCaptor.capture());

        List<SseEmitter.SseEventBuilder> sentEvents = eventCaptor.getAllValues();
        assertSentEvent(sentEvents.get(0), firstEvent);
        assertSentEvent(sentEvents.get(1), secondEvent);

        verify(historyStore).snapshot(userId, lastEventId);
        verify(historyStore, never()).append(any());
        assertThat(result).isEqualTo(secondEntry.position());

    }
    @Test
    @DisplayName("같은 사용자의 두 연결에 각자의 마지막 이벤트 ID 이후 복구 목록만 전송한다.")
    void replaysSnapshotsIndependentlyForConnectionsOfSameUser() throws IOException {
        Connection secondConnection = Connection.create();
        SseEmitter secondEmitter = mock(SseEmitter.class);
        SseEvent<JsonNode> firstEvent = SseEvent.createNotificationEvent(
                UUID.randomUUID(),
                userId,
                jsonMapper.readTree("""
                        {
                            "message": "첫 번째 알림"
                        }
                        """));
        SseEvent<JsonNode> secondEvent = SseEvent.createNotificationEvent(
                UUID.randomUUID(),
                userId,
                jsonMapper.readTree("""
                        {
                            "message": "두 번째 알림"
                        }
                        """));

        SseHistoryEntry firstEntry = new SseHistoryEntry(createHistoryPosition(), firstEvent);
        SseHistoryEntry secondEntry = new SseHistoryEntry(createHistoryPosition("1000-1"), secondEvent);

        when(historyStore.snapshot(userId, lastEventId))
                .thenReturn(new SseHistorySnapshot(secondEntry.position(),
                        List.of(firstEntry, secondEntry)));

        when(historyStore.snapshot(userId, firstEvent.eventId()))
                .thenReturn(new SseHistorySnapshot(secondEntry.position(), List.of(secondEntry)));

        service.recover(userId, connection, emitter, lastEventId);
        service.recover(userId, secondConnection, secondEmitter, firstEvent.eventId());

        ArgumentCaptor<SseEmitter.SseEventBuilder> firstCaptor = ArgumentCaptor.forClass(SseEmitter.SseEventBuilder.class);
        ArgumentCaptor<SseEmitter.SseEventBuilder> secondCaptor = ArgumentCaptor.forClass(SseEmitter.SseEventBuilder.class);

        verify(emitter, times(2)).send(firstCaptor.capture());
        verify(secondEmitter).send(secondCaptor.capture());
        List<SseEmitter.SseEventBuilder> firstSentEvents = firstCaptor.getAllValues();
        assertSentEvent(firstSentEvents.get(0), firstEvent);
        assertSentEvent(firstSentEvents.get(1), secondEvent);
        assertSentEvent(secondCaptor.getValue(), secondEvent);

        verify(historyStore).snapshot(userId, lastEventId);
        verify(historyStore).snapshot(userId, firstEvent.eventId());

    }

    @Test
    @DisplayName("스냅샷 조회에 실패하면 원래 예외를 전달하고 이벤트를 전송하지 않는다.")
    void propagatesSnapshotFailureWithoutSendingEvents() throws IOException {
        DataAccessResourceFailureException failure = new DataAccessResourceFailureException("테스트용 이력 조회 실패");

        when(historyStore.snapshot(userId, lastEventId)).thenThrow(failure);

        assertThatThrownBy(() -> service.recover(userId, connection, emitter, lastEventId))
                .isSameAs(failure);

        verify(historyStore).snapshot(userId, lastEventId);
        verify(emitter, never()).send(any(SseEmitter.SseEventBuilder.class));
    }

    @Test
    @DisplayName("전송 중 IOException이 발생하면 원인을 보존한 예외를 전달하고 나머지 전송을 중단한다.")
    void stopsReplayAndPreservesCauseWhenSendingFailsWithIoException() throws IOException {
        SseHistoryEntry firstEntry = new SseHistoryEntry(createHistoryPosition(),
                SseEvent.createNotificationEvent(UUID.randomUUID(),
                        userId,
                        jsonMapper.readTree("{}")));

        SseHistoryEntry secondEntry = new SseHistoryEntry(createHistoryPosition("1000-1"),
                SseEvent.createNotificationEvent(UUID.randomUUID(),
                        userId,
                        jsonMapper.readTree("{}")));

        when(historyStore.snapshot(userId, lastEventId))
                .thenReturn(new SseHistorySnapshot(
                        secondEntry.position(),
                        List.of(firstEntry, secondEntry)
                ));

        IOException failure = new IOException("테스트용 복구 이벤트 전송 실패");

        doThrow(failure)
                .when(emitter)
                .send(any(SseEmitter.SseEventBuilder.class));

        assertThatThrownBy(() -> service.recover(userId, connection, emitter, lastEventId))
                .isInstanceOf(UncheckedIOException.class)
                .hasCause(failure);

        ArgumentCaptor<SseEmitter.SseEventBuilder> eventCaptor = ArgumentCaptor.forClass(SseEmitter.SseEventBuilder.class);
        verify(emitter).send(eventCaptor.capture());
        assertSentEvent(eventCaptor.getValue(), firstEntry.event());
        verify(historyStore).snapshot(userId, lastEventId);

    }


    @Test
    @DisplayName("전송 중 IllegalStateException이 발생하면 원인을 보존한 예외를 전달하고 나머지 전송을 중단한다.")
    void stopsReplayAndPropagatesIllegalStateException() throws IOException {
        SseHistoryEntry firstEntry = new SseHistoryEntry(createHistoryPosition(),
                SseEvent.createNotificationEvent(UUID.randomUUID(),
                        userId,
                        jsonMapper.readTree("{}")));

        SseHistoryEntry secondEntry = new SseHistoryEntry(createHistoryPosition("1000-1"),
                SseEvent.createNotificationEvent(UUID.randomUUID(),
                        userId,
                        jsonMapper.readTree("{}")));

        when(historyStore.snapshot(userId, lastEventId))
                .thenReturn(new SseHistorySnapshot(
                        secondEntry.position(),
                        List.of(firstEntry, secondEntry)
                ));

        IllegalStateException failure = new IllegalStateException("테스트용 복구 이벤트 전송 상태 오류");

        doThrow(failure)
                .when(emitter)
                .send(any(SseEmitter.SseEventBuilder.class));

        assertThatThrownBy(() -> service.recover(userId, connection, emitter, lastEventId))
                .isSameAs(failure);

        ArgumentCaptor<SseEmitter.SseEventBuilder> eventCaptor = ArgumentCaptor.forClass(SseEmitter.SseEventBuilder.class);
        verify(emitter).send(eventCaptor.capture());
        assertSentEvent(eventCaptor.getValue(), firstEntry.event());
        verify(historyStore).snapshot(userId, lastEventId);

    }

    @Test
    @DisplayName("지정한 위치 이후 이력을 조회하고 결과가 비어 있으면 기존 위치를 반환한다.")
    void readsHistoryAfterPositionAndKeepsPositionWhenEmpty() throws IOException {
        SseHistoryPosition afterPosition = createHistoryPosition();
        when(historyStore.readAfter(userId, afterPosition))
                .thenReturn(new SseHistoryReadResult(false, List.of()));

        SseHistoryPosition result = service.recoverAfter(userId, connection, emitter, afterPosition);

        verify(historyStore).readAfter(userId, afterPosition);
        assertThat(result).isEqualTo(afterPosition);
        verify(emitter, never()).send(any(SseEmitter.SseEventBuilder.class));
    }

    @Test
    @DisplayName("기준 위치가 null이어도 이력을 조회하고 결과가 비어 있으면 null을 반환한다.")
    void readsHistoryWithoutPositionAndReturnsNullWhenEmpty() throws IOException {
        when(historyStore.readAfter(userId, null))
                .thenReturn(new SseHistoryReadResult(false, List.of()));

        SseHistoryPosition result = service.recoverAfter(userId, connection, emitter, null);

        verify(historyStore).readAfter(userId, null);
        assertThat(result).isNull();
        verify(emitter, never()).send(any(SseEmitter.SseEventBuilder.class));
    }

    @Test
    @DisplayName("후속 이벤트를 조회 순서대로 전송하고 마지막 이벤트의 저장 위치를 반환한다.")
    void sendsFollowingEventsInOrderAndReturnsLastPosition() throws IOException {
        SseHistoryPosition afterPosition = createHistoryPosition();

        SseEvent<JsonNode> firstEvent = SseEvent.createNotificationEvent(
                UUID.fromString("00000000-0000-0000-0000-000000000002"),
                userId,
                jsonMapper.readTree("""
                        {"message": "첫 번째 후속 알림"}
                        """)
        );

        SseEvent<JsonNode> secondEvent = SseEvent.createNotificationEvent(
                UUID.fromString("00000000-0000-0000-0000-000000000001"),
                userId,
                jsonMapper.readTree("""
                        {"message": "두 번째 후속 알림"}
                        """)
        );

        SseHistoryEntry firstEntry = new SseHistoryEntry(createHistoryPosition("1000-1"), firstEvent);
        SseHistoryEntry secondEntry = new SseHistoryEntry(createHistoryPosition("1000-2"), secondEvent);

        when(historyStore.readAfter(userId, afterPosition))
                .thenReturn(new SseHistoryReadResult(false, List.of(firstEntry, secondEntry)));

        SseHistoryPosition result = service.recoverAfter(userId, connection, emitter, afterPosition);
        verify(historyStore).readAfter(userId, afterPosition);

        ArgumentCaptor<SseEmitter.SseEventBuilder> eventCaptor = ArgumentCaptor.forClass(SseEmitter.SseEventBuilder.class);
        verify(emitter, times(2)).send(eventCaptor.capture());

        List<SseEmitter.SseEventBuilder> sentEvents = eventCaptor.getAllValues();
        assertSentEvent(sentEvents.get(0), firstEvent);
        assertSentEvent(sentEvents.get(1), secondEvent);
        assertThat(result).isEqualTo(secondEntry.position());
    }

    @Test
    @DisplayName("후속 이력의 유실이 감지되면 이벤트를 전송하지 않고 예외를 전달한다.")
    void throwsIllegalStateExceptionWithoutSendingWhenHistoryGapDetected() throws IOException {
        SseHistoryPosition afterPosition = createHistoryPosition();

        when(historyStore.readAfter(userId, afterPosition))
                .thenReturn(new SseHistoryReadResult(true, List.of()));

        assertThatThrownBy(() -> service.recoverAfter(userId, connection, emitter, afterPosition))
                .isInstanceOf(IllegalStateException.class);

        verify(historyStore).readAfter(userId, afterPosition);
        verify(emitter, never()).send(any(SseEmitter.SseEventBuilder.class));

    }

    @Test
    @DisplayName("후속 이력 조회에 실패하면 원래 예외를 전달하고 이벤트를 전송하지 않는다.")
    void propagatesFollowingHistoryFailureWithoutSendingEvents() throws IOException {
        SseHistoryPosition afterPosition = createHistoryPosition();
        DataAccessResourceFailureException failure = new DataAccessResourceFailureException("테스트용 후속 이력 조회 실패");

        when(historyStore.readAfter(userId, afterPosition)).thenThrow(failure);

        assertThatThrownBy(() -> service.recoverAfter(userId, connection, emitter, afterPosition))
                .isSameAs(failure);

        verify(historyStore).readAfter(userId, afterPosition);
        verify(emitter, never()).send(any(SseEmitter.SseEventBuilder.class));
    }

    private void assertSentEvent(SseEmitter.SseEventBuilder builder,
                                 SseEvent<JsonNode> expected){
        List<ResponseBodyEmitter.DataWithMediaType> parts = List.copyOf(builder.build());
        assertThat(parts).hasSize(3);
        assertThat(parts.get(0).getData()).isInstanceOf(String.class);

        assertThat((String)parts.get(0).getData())
                .contains("id:" + expected.eventId() + "\n",
                        "event:" + expected.eventName() + "\n",
                        "data:");

        assertThat(parts.get(1).getMediaType())
                .isEqualTo(MediaType.APPLICATION_JSON);
        Object payload = parts.get(1).getData();
        JsonNode actualData = payload instanceof String json ?
                jsonMapper.readTree(json)
                : jsonMapper.valueToTree(payload);

        assertThat(actualData).isEqualTo(expected.data());
        assertThat(parts.get(2).getData()).isEqualTo("\n\n");

    }

    private SseHistoryPosition createHistoryPosition() {
        return createHistoryPosition("1000-0");
    }

    private SseHistoryPosition createHistoryPosition(String position) {
        return new SseHistoryPosition(position);
    }
}