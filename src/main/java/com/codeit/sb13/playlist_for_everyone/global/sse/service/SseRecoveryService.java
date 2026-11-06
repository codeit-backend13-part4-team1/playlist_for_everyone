package com.codeit.sb13.playlist_for_everyone.global.sse.service;

import com.codeit.sb13.playlist_for_everyone.global.sse.event.Connection;
import com.codeit.sb13.playlist_for_everyone.global.sse.event.SseEvent;
import com.codeit.sb13.playlist_for_everyone.global.sse.history.SseHistoryPosition;
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
                .forEach(entry ->{
                    SseEvent<JsonNode> event = entry.event();
                    try {
                        emitter.send(SseEmitter.event()
                                .id(event.eventId().toString())
                                .name(event.eventName())
                                .data(event.data(), MediaType.APPLICATION_JSON)
                        );
                    } catch (IOException e) {
                        throw new UncheckedIOException("SSE 복구 이벤트 전송에 실패했습니다.",e);
                    }
                });

        return snapshot.upperBound();
    }

}
