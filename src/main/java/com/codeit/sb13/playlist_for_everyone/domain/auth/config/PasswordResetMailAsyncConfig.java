package com.codeit.sb13.playlist_for_everyone.domain.auth.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.ThreadPoolExecutor;

@Configuration(proxyBeanMethods = false)
@EnableAsync
public class PasswordResetMailAsyncConfig {

    public static final String EXECUTOR_NAME = "passwordResetMailExecutor";

    /** 임시 비밀번호 메일만 보내는 실행기. 메일 서버가 느려도 알림 같은 다른 비동기 작업을 막지 않는다. */
    @Bean(name = EXECUTOR_NAME, defaultCandidate = false)
    public ThreadPoolTaskExecutor passwordResetMailExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(2);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("password-reset-mail-");
        // 대기열이 차면 요청 스레드가 직접 보낸다. 작업을 버리면 메일 없이 사용 가능한 임시 비밀번호가 남는다.
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        // 배포로 종료될 때 보내던 메일과 재시도를 마치고 내려간다. (최대 3번 × SMTP 타임아웃 5초 + 대기 3초)
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(20);
        return executor;
    }
}
