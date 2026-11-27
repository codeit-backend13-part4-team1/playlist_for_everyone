package com.codeit.sb13.playlist_for_everyone.global.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

/**
 * 스케줄 실행을 활성화하고 SSE 하트비트 주기 스케줄러를 등록한다.
 * 컨텍스트 종료 시 주기 작업 취소와 스케줄러 스레드 정리는 Spring이 담당한다.
 */
@Configuration(proxyBeanMethods = false)
@EnableScheduling
public class SchedulerConfig {

    /**
     * Scheduled의 scheduler 이름으로 선택하는 전용 주기 스케줄러다.
     * 한 스레드에서 하트비트 작업 제출과 로그인 세션 재검증을 시작한다.
     * 실제 하트비트 쓰기는 sseHeartbeatTaskExecutor의 워커가 담당한다.
     * defaultCandidate=false로 타입만으로 주입하는 기본 후보에서 제외한다.
     */
    @Bean(name = "sseHeartbeatTaskScheduler", defaultCandidate = false)
    public ThreadPoolTaskScheduler sseHeartbeatTaskScheduler(){
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(1);
        // 로그와 테스트에서 하트비트 스케줄러 스레드를 식별할 수 있게 한다.
        scheduler.setThreadNamePrefix("sse-heartbeat-");
        return scheduler;
    }
}
