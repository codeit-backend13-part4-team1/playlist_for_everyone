package com.codeit.sb13.playlist_for_everyone.global.sse.service;

import com.codeit.sb13.playlist_for_everyone.global.sse.event.Connection;
import com.codeit.sb13.playlist_for_everyone.global.sse.history.SseHistoryStore;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class SseRecoveryService {

    private final SseHistoryStore historyStore;

    public void recover(
            UUID userId,
            Connection connection,
            SseEmitter emitter,
            UUID lastEventId
    ){

    }
}
