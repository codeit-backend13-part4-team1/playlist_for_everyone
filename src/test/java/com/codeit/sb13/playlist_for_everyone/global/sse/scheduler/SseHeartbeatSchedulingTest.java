package com.codeit.sb13.playlist_for_everyone.global.sse.scheduler;

import com.codeit.sb13.playlist_for_everyone.global.config.SseConfig;
import com.codeit.sb13.playlist_for_everyone.global.sse.service.SseDeliveryService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Answers;
import org.mockito.ArgumentCaptor;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

import java.time.Duration;
import java.time.Instant;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

@SpringJUnitConfig({
        SseConfig.class,
        SseHeartbeatScheduler.class
})
@TestPropertySource(properties = {
        "everyone.sse.heartbeat-interval=3s",
        "everyone.sse.connection-timeout=30m"
})
@DisplayName("SSE 하트비트 주기 실행 설정")
public class SseHeartbeatSchedulingTest {

    @MockitoBean
    private SseDeliveryService sseDeliveryService;

    @MockitoBean(
            name = "sseHeartbeatTaskScheduler",
            answers = Answers.CALLS_REAL_METHODS
    )
    private TaskScheduler taskScheduler;

    @Test
    @DisplayName("설정된 간격으로 하트비트 작업을 등록하고 등록된 작업은 전송 서비스를 호출한다.")
    void registersHeartbeatWithConfiguredInterval(){
        ArgumentCaptor<Runnable> taskCaptor = ArgumentCaptor.forClass(Runnable.class);

        verify(taskScheduler)
                .scheduleWithFixedDelay(taskCaptor.capture(), any(Instant.class), eq(Duration.ofSeconds(3)));

        taskCaptor.getValue().run();

        verify(sseDeliveryService).sendHeartbeatToLocalConnections();

    }
}
