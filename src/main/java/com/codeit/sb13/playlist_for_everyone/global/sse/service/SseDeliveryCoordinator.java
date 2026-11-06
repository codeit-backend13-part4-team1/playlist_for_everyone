package com.codeit.sb13.playlist_for_everyone.global.sse.service;

import com.codeit.sb13.playlist_for_everyone.global.sse.event.Connection;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.UUID;

@Component
@RequiredArgsConstructor
public class SseDeliveryCoordinator {

    private final SseRecoveryService recoveryService;

    public void register(UUID userId, Connection connection, SseEmitter emitter){

    }

    public void recover(UUID userId, Connection connection, UUID lastEventId){

    }

    public void remove(UUID userId, Connection connection){

    }
}
