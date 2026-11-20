package com.codeit.sb13.playlist_for_everyone.global.config;

import org.slf4j.MDC;
import org.springframework.core.task.TaskDecorator;
import org.springframework.security.concurrent.DelegatingSecurityContextRunnable;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Map;

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
