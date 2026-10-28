package com.codeit.sb13.playlist_for_everyone.global.sse.scheduler;

import com.codeit.sb13.playlist_for_everyone.global.sse.service.SseDeliveryService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("SSE 하트비트 스케줄러")
class SseHeartbeatSchedulerTest {

    @Mock
    private SseDeliveryService sseDeliveryService;

    @InjectMocks
    private SseHeartbeatScheduler scheduler;

    @Test
    @DisplayName("하트비트 실행 시 로컬 연결 전송을 서비스에 요청한다.")
    void sendHeartbeatDelegatesToDeliveryService(){
        scheduler.sendHeartbeat();

        verify(sseDeliveryService).sendHeartbeatToLocalConnections();
    }



}