package com.codeit.sb13.playlist_for_everyone.domain.auth.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.ThreadPoolExecutor;

@Configuration(proxyBeanMethods = false)
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
        // 대기열이 차면 작업을 거부한다. 리스너가 거부를 받아 발급을 폐기하고, 요청 스레드에서 대신 보내지 않는다.
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.AbortPolicy());
        // 배포로 종료될 때 보내던 메일과 재시도를 마치고 내려간다. (최대 3번 × SMTP 타임아웃 5초 + 대기 3초)
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(20);
        return executor;
    }
}
