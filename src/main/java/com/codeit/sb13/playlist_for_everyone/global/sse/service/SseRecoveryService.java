package com.codeit.sb13.playlist_for_everyone.global.sse.service;

import com.codeit.sb13.playlist_for_everyone.global.sse.event.Connection;
import com.codeit.sb13.playlist_for_everyone.global.sse.event.SseEvent;
import com.codeit.sb13.playlist_for_everyone.global.sse.history.*;
import com.codeit.sb13.playlist_for_everyone.global.sse.replay.SseReplayFilter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import tools.jackson.databind.JsonNode;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class SseRecoveryService {

    private final SseHistoryStore historyStore;
    private final List<SseReplayFilter> replayFilters;

    public SseHistoryPosition recover(
            UUID userId,
            Connection connection,
            SseEmitter emitter,
            UUID lastEventId
    ){
        SseHistorySnapshot snapshot = historyStore.snapshot(userId, lastEventId);
        filterReplayEntries(userId, snapshot.entries())
                .forEach(entry ->
                        sendEvent(emitter, entry.event()));

        return snapshot.upperBound();
    }

    public SseHistoryPosition recoverAfter(
            UUID userId,
            Connection connection,
            SseEmitter emitter,
            SseHistoryPosition afterPosition
    ){
        SseHistoryReadResult result = historyStore.readAfter(userId, afterPosition);

        if (result.gapDetected()){
            log.debug(
                    """
                    SSE 기준 위치가 보관 이력에 없어 남은 후속 이력을 처리합니다.
                    userId={}, connectionId={}, afterPosition={}, remainingCount={}
                    """,
                    userId,
                    connection.id(),
                    afterPosition,
                    result.entries().size()
            );
        }
        List<SseHistoryEntry> entries = result.entries();

        if (entries.isEmpty()) {
            return afterPosition;
        }

        filterReplayEntries(userId, entries)
                .forEach(entry -> sendEvent(emitter, entry.event()));

        return entries.get(entries.size() - 1).position();
    }

    private void sendEvent(SseEmitter emitter, SseEvent<JsonNode> event) {
        try {
            emitter.send(SseEmitter.event()
                    .id(event.eventId().toString())
                    .name(event.eventName())
                    .data(event.data(), MediaType.APPLICATION_JSON)
            );
        } catch (IOException e) {
            throw new UncheckedIOException("SSE 복구 이벤트 전송에 실패했습니다.",e);
        }
    }

    private List<SseHistoryEntry> filterReplayEntries(UUID receiverId, List<SseHistoryEntry> entries){
        if (entries.isEmpty() || replayFilters.isEmpty()){
            return entries;
        }

        LinkedHashMap<String, List<SseHistoryEntry>> groupedEntries = entries.stream()
                .collect(Collectors.groupingBy(entry -> entry.event().eventName(),
                        LinkedHashMap::new,
                        Collectors.toList()));

        Set<SseHistoryPosition> allowedPositions = new HashSet<>();
        for (Map.Entry<String, List<SseHistoryEntry>> group : groupedEntries.entrySet()){
            SseReplayFilter replayFilter = replayFilters.stream()
                    .filter(filter -> filter.supports(group.getKey()))
                    .findFirst()
                    .orElse(null);

            List<SseHistoryEntry> candidates = group.getValue();
            List<SseHistoryEntry> retainedEntries = replayFilter == null ?
                    candidates : replayFilter.filter(receiverId, candidates);

            retainedEntries.forEach(entry -> allowedPositions.add(entry.position()));
        }
        return entries.stream()
                .filter(entry -> allowedPositions.contains(entry.position()))
                .toList();
    }

}
