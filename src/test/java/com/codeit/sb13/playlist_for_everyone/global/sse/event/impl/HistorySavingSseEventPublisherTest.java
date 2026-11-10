package com.codeit.sb13.playlist_for_everyone.global.sse.event.impl;

import com.codeit.sb13.playlist_for_everyone.global.exception.sse.SseHistoryConflictException;
import com.codeit.sb13.playlist_for_everyone.global.exception.sse.SsePublishException;
import com.codeit.sb13.playlist_for_everyone.global.sse.event.Event;
import com.codeit.sb13.playlist_for_everyone.global.sse.event.SseEvent;
import com.codeit.sb13.playlist_for_everyone.global.sse.event.SseEventPublisher;
import com.codeit.sb13.playlist_for_everyone.global.sse.history.SseHistoryEntry;
import com.codeit.sb13.playlist_for_everyone.global.sse.history.SseHistoryPosition;
import com.codeit.sb13.playlist_for_everyone.global.sse.history.SseHistoryStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataAccessResourceFailureException;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("SSE 이력 저장 후 이벤트 발행")
class HistorySavingSseEventPublisherTest {

    @Mock
    private SseHistoryStore historyStore;

    @Mock
    private SseEventPublisher delegate;

    private ObjectMapper objectMapper;
    private SseEventPublisher publisher;
    private final UUID eventId = UUID.randomUUID();
    private final UUID receiverId = UUID.randomUUID();

    @BeforeEach
    void setUp(){
        objectMapper = JsonMapper.builder().build();
        publisher = new HistorySavingSseEventPublisher(historyStore, delegate, objectMapper);
    }

    @Test
    @DisplayName("이력을 먼저 저장하고 저장된 이벤트를 전송 발행기에 한 번 전달한다.")
    void savesHistoryBeforePublishingEvent(){

        Map<String, String> data = createEventData();
        SseEvent<Map<String, String>> event = createNotificationSseEvent(data);

        JsonNode jsonData = objectMapper.valueToTree(data);
        SseEvent<JsonNode> historyEvent = createNotificationSseEvent(jsonData);

        SseHistoryEntry savedEntry = new SseHistoryEntry(createHistoryPosition(), historyEvent);

        when(historyStore.append(historyEvent)).thenReturn(savedEntry);
        publisher.publish(event);

        InOrder order = inOrder(historyStore, delegate);
        order.verify(historyStore).append(historyEvent);
        order.verify(delegate).publish(savedEntry.event());
        verifyNoMoreInteractions(historyStore, delegate);
    }

    @Test
    @DisplayName("DM 이벤트의 이름과 식별자 및 데이터를 유지하여 이력을 저장하고 발행한다.")
    void savesAndPublishesDirectMessageEvent(){
        Map<String, String> data = createEventData("content", "테스트 메시지");

        SseEvent<Map<String, String>> event = new SseEvent<>(eventId, receiverId, Event.DIRECT_MESSAGES.getName(), data);

        JsonNode jsonData = objectMapper.valueToTree(data);
        SseEvent<JsonNode> historyEvent = new SseEvent<>(eventId, receiverId, Event.DIRECT_MESSAGES.getName(), jsonData);
        SseHistoryEntry savedEntry = new SseHistoryEntry(createHistoryPosition(), historyEvent);

        when(historyStore.append(historyEvent)).thenReturn(savedEntry);
        publisher.publish(event);

        verify(historyStore).append(historyEvent);
        verify(delegate).publish(savedEntry.event());
        verifyNoMoreInteractions(historyStore, delegate);
    }

    @Test
    @DisplayName("데이터가 null인 이벤트도 JSON null 의미를 유지하여 이력을 저장하고 발행한다.")
    void savesAndPublishesEventWithNullData(){
        SseEvent<JsonNode> event = createNotificationSseEvent(null);

        JsonNode jsonData = objectMapper.valueToTree(event.data());
        SseEvent<JsonNode> historyEvent = createNotificationSseEvent(jsonData);
        SseHistoryEntry savedEntry = new SseHistoryEntry(createHistoryPosition(), historyEvent);

        when(historyStore.append(historyEvent)).thenReturn(savedEntry);
        publisher.publish(event);

        verify(historyStore).append(historyEvent);
        verify(delegate).publish(savedEntry.event());
        verifyNoMoreInteractions(historyStore, delegate);
    }

    @Test
    @DisplayName("JSON 변환에 실패하면 원인을 보존한 발행 예외를 던지고 이력 저장과 전송을 수행하지 않는다.")
    void serializationFailureStopsHistorySavingAndPublishing(){
        SseEvent<SerializationFailingPayload> event = createNotificationSseEvent(new SerializationFailingPayload());
        assertThatThrownBy(() -> publisher.publish(event))
                .isInstanceOf(SsePublishException.class)
                .hasCauseInstanceOf(JacksonException.class);

        verifyNoInteractions(historyStore, delegate);
    }

    @Test
    @DisplayName("이력 저장소 접근에 실패하면 원인을 보존한 발행 예외를 던지고 전송하지 않는다.")
    void historyAccessFailureStopsPublishing(){
        Map<String, String> data = createEventData();
        SseEvent<Map<String, String>> event = createNotificationSseEvent(data);

        JsonNode jsonData = objectMapper.valueToTree(data);
        SseEvent<JsonNode> historyEvent = createNotificationSseEvent(jsonData);

        DataAccessResourceFailureException failure = new DataAccessResourceFailureException("테스트용 이력 저장소 접근 실패");

        when(historyStore.append(historyEvent)).thenThrow(failure);
        assertThatThrownBy(() -> publisher.publish(event))
                .isInstanceOf(SsePublishException.class)
                .hasCause(failure);

        verify(historyStore).append(historyEvent);
        verifyNoMoreInteractions(historyStore);
        verifyNoInteractions(delegate);

    }

    @Test
    @DisplayName("이력 충돌이 발생하면 원인을 보존한 발행 예외를 던지고 전송하지 않는다.")
    void historyConflictStopsPublishing(){
        Map<String, String> data = createEventData();
        SseEvent<Map<String, String>> event = createNotificationSseEvent(data);

        JsonNode jsonData = objectMapper.valueToTree(data);
        SseEvent<JsonNode> historyEvent = createNotificationSseEvent(jsonData);

        SseHistoryConflictException failure = new SseHistoryConflictException(receiverId, eventId);
        when(historyStore.append(historyEvent)).thenThrow(failure);
        assertThatThrownBy(() -> publisher.publish(event))
                .isInstanceOf(SsePublishException.class)
                .hasCause(failure);

        verify(historyStore).append(historyEvent);
        verifyNoMoreInteractions(historyStore);
        verifyNoInteractions(delegate);
    }

    @Test
    @DisplayName("이력 저장 결과 해석에 실패하면 원인을 보존한 발행 예외를 던지고 전송하지 않는다.")
    void historyResultParsingFailureStopsPublishing(){
        Map<String, String> data = createEventData();
        SseEvent<Map<String, String>> event = createNotificationSseEvent(data);

        JsonNode jsonData = objectMapper.valueToTree(data);
        SseEvent<JsonNode> historyEvent = createNotificationSseEvent(jsonData);

        IllegalStateException failure = new IllegalStateException("테스트용 이력 저장 결과 해석 실패");

        when(historyStore.append(historyEvent)).thenThrow(failure);
        assertThatThrownBy(() -> publisher.publish(event))
                .isInstanceOf(SsePublishException.class)
                .hasCause(failure);

        verify(historyStore).append(historyEvent);
        verifyNoMoreInteractions(historyStore);
        verifyNoInteractions(delegate);
    }

    @Test
    @DisplayName("이력 저장 후 전송에 실패하면 기존 발행 예외를 그대로 전달하고 재시도하지 않는다.")
    void transportFailurePropagatesWithoutRetry(){
        Map<String, String> data = createEventData();
        SseEvent<Map<String, String>> event = createNotificationSseEvent(data);

        JsonNode jsonData = objectMapper.valueToTree(data);
        SseEvent<JsonNode> historyEvent = createNotificationSseEvent(jsonData);

        SseHistoryEntry savedEntry = new SseHistoryEntry(createHistoryPosition(), historyEvent);
        SsePublishException failure = new SsePublishException(new DataAccessResourceFailureException("테스트용 전송 실패"));
        when(historyStore.append(historyEvent)).thenReturn(savedEntry);
        doThrow(failure).when(delegate).publish(savedEntry.event());

        assertThatThrownBy(() -> publisher.publish(event))
                .isSameAs(failure);

        InOrder order = inOrder(historyStore, delegate);
        order.verify(historyStore).append(historyEvent);
        order.verify(delegate).publish(savedEntry.event());
        verifyNoMoreInteractions(historyStore, delegate);
    }

    private <T> SseEvent<T> createNotificationSseEvent(T jsonData) {
        return SseEvent.createNotificationEvent(eventId, receiverId, jsonData);
    }

    private SseHistoryPosition createHistoryPosition() {
        return new SseHistoryPosition("1000-0");
    }

    private Map<String, String> createEventData(String key, String value) {
        return Map.of(key, value);
    }

    private Map<String, String> createEventData() {
        return createEventData("message", "테스트 알림");
    }

    public static class SerializationFailingPayload{
        public String getMessage(){
            throw new IllegalStateException("테스트용 JSON 변환 실패");
        }
    }
}
