package com.codeit.sb13.playlist_for_everyone.global.sse.scheduler;

import com.codeit.sb13.playlist_for_everyone.global.sse.service.SseDeliveryService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.verify;

/**
 * 스케줄 메서드가 전송 서비스에 일을 위임하는지만 확인한다.
 * 실제 주기 등록은 SseHeartbeatSchedulingTest, 실제 실행기 동작은 SchedulerConfigTest에서 검증한다.
 */
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
