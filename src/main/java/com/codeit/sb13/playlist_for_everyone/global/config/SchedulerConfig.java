package com.codeit.sb13.playlist_for_everyone.global.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

/**
 * 스케줄 실행을 활성화하고 SSE 하트비트용 실행기를 등록한다.
 * 실행기는 Spring 빈으로 관리하므로 컨텍스트 종료 시 작업 취소와 스레드 정리도 Spring이 담당한다.
 */
@Configuration(proxyBeanMethods = false)
@EnableScheduling
public class SchedulerConfig {

    /**
     * Scheduled의 scheduler 이름으로 선택하는 전용 실행기다.
     * defaultCandidate=false로 타입만으로 주입하는 기본 후보에서 제외한다.
     * 다른 스케줄 작업과 실행 자원을 분리하되, 하트비트 작업끼리는 한 스레드에서 처리한다.
     */
    @Bean(name = "sseHeartbeatTaskScheduler", defaultCandidate = false)
    public ThreadPoolTaskScheduler sseHeartbeatTaskScheduler(){
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(1);
        // 로그와 테스트에서 하트비트 실행 스레드를 식별할 수 있게 한다.
        scheduler.setThreadNamePrefix("sse-heartbeat-");
        return scheduler;
    }
}
