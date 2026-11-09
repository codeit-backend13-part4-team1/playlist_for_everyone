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

/**
 * 공통 발행 계약을 감싸서 JSON 변환과 공유 이력 저장을 브로커 발행보다 먼저 수행한다.
 * 수신자의 접속 여부와 무관하게 저장하며, 호출부는 DTO 객체를 그대로 넘긴다.
 *
 * <p>저장 실패는 SsePublishException으로 전달하고 브로커를 호출하지 않는다.
 * 저장 후 브로커가 실패해도 이력을 삭제하거나 자동으로 다시 발행하지 않는다.
 * 두 작업은 하나의 트랜잭션이 아니며, 반환은 브라우저의 수신 확인이 아니다.
 */
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
        // 중복 저장이면 기존에 저장한 이벤트를 사용한다. 브로커 신호 자체는 다시 발행될 수 있다.
        delegate.publish(savedEntry.event());
    }
}
