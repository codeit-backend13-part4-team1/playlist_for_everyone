package com.codeit.sb13.playlist_for_everyone.global.sse.scheduler;

import com.codeit.sb13.playlist_for_everyone.global.sse.service.SseDeliveryService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class SseHeartbeatScheduler {

    private final SseDeliveryService sseDeliveryService;

    public void sendHeartbeat(){
        sseDeliveryService.sendHeartbeatToLocalConnections();
    }
}
