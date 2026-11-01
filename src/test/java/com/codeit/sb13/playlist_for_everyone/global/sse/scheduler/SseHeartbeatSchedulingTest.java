package com.codeit.sb13.playlist_for_everyone.global.sse.scheduler;

import com.codeit.sb13.playlist_for_everyone.global.config.SchedulerConfig;
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

/**
 * Spring이 Scheduled 설정을 읽고 지정한 실행기에 올바른 간격으로 작업을 등록하는지 확인한다.
 * 실행기를 Mock으로 대체하므로 실제 시간이 흐르기를 기다리지 않고 등록된 Runnable을 직접 실행한다.
 */
@SpringJUnitConfig({
        SseConfig.class,
        SseHeartbeatScheduler.class,
        SchedulerConfig.class
})
@TestPropertySource(properties = {
        "everyone.sse.heartbeat-interval=3s",
        "everyone.sse.connection-timeout=30m"
})
@DisplayName("SSE 하트비트 주기 실행 설정")
public class SseHeartbeatSchedulingTest {

    @MockitoBean
    private SseDeliveryService sseDeliveryService;

    // 운영 코드가 이름으로 선택하는 실행기만 대체한다.
    // CALLS_REAL_METHODS로 getClock 같은 default 메서드의 기본 동작을 사용하고 실제 작업 예약은 Mock으로 받는다.
    @MockitoBean(
            name = "sseHeartbeatTaskScheduler",
            answers = Answers.CALLS_REAL_METHODS
    )
    private TaskScheduler taskScheduler;

    @Test
    @DisplayName("설정된 간격으로 하트비트 작업을 등록하고 등록된 작업은 전송 서비스를 호출한다.")
    void registersHeartbeatWithConfiguredInterval(){
        ArgumentCaptor<Runnable> taskCaptor = ArgumentCaptor.forClass(Runnable.class);

        // 현재 시각 자체는 고정하지 않고, 설정한 3초 간격과 실행할 작업이 전달되는지를 확인한다.
        verify(taskScheduler)
                .scheduleWithFixedDelay(taskCaptor.capture(), any(Instant.class), eq(Duration.ofSeconds(3)));

        // 시간 대기 없이 실제 등록한 작업을 실행하여 전송 서비스까지 호출되는지 확인한다.
        taskCaptor.getValue().run();

        verify(sseDeliveryService).sendHeartbeatToLocalConnections();

    }
}
