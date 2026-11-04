package com.codeit.sb13.playlist_for_everyone.global.websocket;

import com.codeit.sb13.playlist_for_everyone.domain.auth.security.SecurityUser;
import jakarta.annotation.Nullable;
import org.springframework.messaging.Message;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.security.core.Authentication;

import java.security.Principal;
import java.util.Objects;

/**
 * 웹 소켓 메세지에서 세션 및 사용자 정보를 추출하는 클래스.
 * message 에서 확인 가능한 정보를 추출 한 것이기 때문에, registry 에 저장된
 * SessionData 와는 차이가 있을 수 있다.
 */

public class SessionInfo {
    private final String session;
    private final String subscription;
    private Principal principal;
    private String destination;

    public SessionInfo(
            Message<?> message
    ){
        StompHeaderAccessor accessor =
                StompHeaderAccessor.wrap(Objects.requireNonNull(message));

        this.session = accessor.getSessionId();
        this.subscription = accessor.getSubscriptionId();
        this.destination = accessor.getDestination();
        this.principal = accessor.getUser();
    }

    public SessionInfo(
            Message<?> message,
            @Nullable Principal principal
    ){
        StompHeaderAccessor accessor =
                StompHeaderAccessor.wrap(Objects.requireNonNull(message));

        this.session = accessor.getSessionId();
        this.subscription = accessor.getSubscriptionId();
        this.destination = accessor.getDestination();
        this.principal = principal;
    }

    // getter
    @Nullable public String session(){ return session; }
    @Nullable public String subscription(){ return subscription; }
    @Nullable public String destination(){ return destination; }
    @Nullable public Principal principal(){ return principal; }
    @Nullable public SecurityUser user(){
        if (principal instanceof Authentication authentication) {
            return (SecurityUser) authentication.getPrincipal();
        } else
            return null;
    }

    // setter
    @Nullable public Principal principal(Principal principal){
        if (principal != null) this.principal = principal;
        return this.principal;
    }
    @Nullable public String destination(String destination){
        if (destination != null) this.destination = destination;
        return this.destination;
    }

    // 편의 매서드
    public SessionInfo wrap(SessionData data){
        destination(data.destination());
        principal(data.principal());
        return this;
    }

    public SessionData toData(){
        return SessionData.builder()
                .session(session)
                .subscription(subscription)
                .destination(destination)
                .principal(principal)
                .build();
    }

}
