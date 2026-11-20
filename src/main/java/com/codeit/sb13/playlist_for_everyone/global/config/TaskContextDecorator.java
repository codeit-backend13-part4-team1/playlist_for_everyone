package com.codeit.sb13.playlist_for_everyone.global.config;

import org.slf4j.MDC;
import org.springframework.core.task.TaskDecorator;
import org.springframework.security.concurrent.DelegatingSecurityContextRunnable;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Map;

/**
 * 작업을 제출할 때 새 {@link SecurityContext}에 호출 스레드의 인증을 담고 MDC를 복사한다.
 * MDC에 {@code requestId}가 있으면 워커 로그에서 같은 값으로 요청을 추적할 수 있다.
 * 작업이 정상 완료되거나 예외로 종료될 때 워커의 이전 인증과 MDC를 복원한다.
 */
public class TaskContextDecorator implements TaskDecorator {

    @Override
    public Runnable decorate(Runnable task) {
        SecurityContext callerContext = SecurityContextHolder.createEmptyContext();
        callerContext.setAuthentication(SecurityContextHolder.getContext().getAuthentication());

        Map<String, String> callerMdc = MDC.getCopyOfContextMap();
        return new DelegatingSecurityContextRunnable(() ->{
            Map<String, String> previousMdc = MDC.getCopyOfContextMap();
            try {
                applyMdc(callerMdc);
                task.run();
            } finally {
                applyMdc(previousMdc);
            }
        }, callerContext);
    }

    private void applyMdc(Map<String, String> contextMap){
        if (contextMap == null){
            MDC.clear();
            return;
        }

        MDC.setContextMap(contextMap);
    }
}
