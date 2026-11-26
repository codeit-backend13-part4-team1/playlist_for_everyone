package com.codeit.sb13.playlist_for_everyone.global.sse.scheduler;

import com.codeit.sb13.playlist_for_everyone.global.sse.service.SseDeliveryService;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 현재 서버의 연결을 대상으로 하트비트 작업 제출과 로그인 세션 재검증을 주기적으로 시작한다.
 * 실제 하트비트 쓰기는 sseHeartbeatTaskExecutor의 워커가 처리한다.
 */
@Component
@RequiredArgsConstructor
public class SseHeartbeatScheduler {

    private final SseDeliveryService sseDeliveryService;

    /**
     * 이전 실행이 끝난 뒤 설정된 간격을 기다린다. 정확한 시각마다 실행하는 fixedRate와 다르다.
     * 명시한 전용 스케줄러에서 동작하며, 각 서버 인스턴스가 자신의 연결에 대해서만 실행한다.
     */
    @Scheduled(
            fixedDelayString = "${everyone.sse.heartbeat-interval:15s}",
            scheduler = "sseHeartbeatTaskScheduler"
    )
    public void sendHeartbeat(){
        sseDeliveryService.sendHeartbeatToLocalConnections();
        sseDeliveryService.revalidateLocalSessions();
    }
}
