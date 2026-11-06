package com.codeit.sb13.playlist_for_everyone.global.sse.service;

import com.codeit.sb13.playlist_for_everyone.global.sse.event.Connection;
import com.codeit.sb13.playlist_for_everyone.global.sse.event.SseEvent;
import com.codeit.sb13.playlist_for_everyone.global.sse.history.SseHistoryPosition;
import com.codeit.sb13.playlist_for_everyone.global.sse.history.SseHistoryReadResult;
import com.codeit.sb13.playlist_for_everyone.global.sse.history.SseHistorySnapshot;
import com.codeit.sb13.playlist_for_everyone.global.sse.history.SseHistoryStore;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import tools.jackson.databind.JsonNode;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

@Service
@RequiredArgsConstructor
public class SseRecoveryService {

    private final SseHistoryStore historyStore;

    public SseHistoryPosition recover(
            UUID userId,
            Connection connection,
            SseEmitter emitter,
            UUID lastEventId
    ){
        SseHistorySnapshot snapshot = historyStore.snapshot(userId, lastEventId);

        snapshot.entries()
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
            throw new IllegalStateException("SSE 이력이 유실되어 연속 복구를 진행할수 없습니다.");
        }

        AtomicReference<SseHistoryPosition> lastPosition = new AtomicReference<>(afterPosition);

        result.entries()
                .forEach(entry -> {
                            sendEvent(emitter, entry.event());
                            lastPosition.set(entry.position());
                        });

        return lastPosition.get();
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

}
