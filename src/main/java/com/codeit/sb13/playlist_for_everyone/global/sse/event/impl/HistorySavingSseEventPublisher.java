package com.codeit.sb13.playlist_for_everyone.global.sse.event.impl;

import com.codeit.sb13.playlist_for_everyone.global.exception.sse.SseHistoryConflictException;
import com.codeit.sb13.playlist_for_everyone.global.exception.sse.SsePublishException;
import com.codeit.sb13.playlist_for_everyone.global.sse.event.SseEvent;
import com.codeit.sb13.playlist_for_everyone.global.sse.event.SseEventPublisher;
import com.codeit.sb13.playlist_for_everyone.global.sse.history.SseHistoryEntry;
import com.codeit.sb13.playlist_for_everyone.global.sse.history.SseHistoryStore;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataAccessException;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@RequiredArgsConstructor
public class HistorySavingSseEventPublisher implements SseEventPublisher {

    private final SseHistoryStore historyStore;
    private final SseEventPublisher delegate;
    private final ObjectMapper objectMapper;

    @Override
    public void publish(SseEvent<?> sseEvent) {
        SseHistoryEntry savedEntry;
        try{
            JsonNode jsonData = objectMapper.valueToTree(sseEvent.data());
            SseEvent<JsonNode> historyEvent = sseEvent.toHistoryData(jsonData);
            savedEntry = historyStore.append(historyEvent);
        } catch (JacksonException
                 | DataAccessException
                 | SseHistoryConflictException
                 | IllegalStateException e){
            throw new SsePublishException(e);
        }
        delegate.publish(savedEntry.event());
    }
}
