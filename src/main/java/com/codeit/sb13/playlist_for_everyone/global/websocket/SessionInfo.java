package com.codeit.sb13.playlist_for_everyone.global.websocket;

import com.codeit.sb13.playlist_for_everyone.domain.auth.security.SecurityUser;
import jakarta.annotation.Nullable;
import org.springframework.messaging.Message;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.security.core.Authentication;

import java.security.Principal;
import java.util.Objects;

public class SessionInfo {
    private final String session;
    @Nullable private final String subscription;
    @Nullable private final SecurityUser user;
    @Nullable private String destination;

    public SessionInfo(
            Message<?> message,
            @Nullable Principal principal
    ){
        Objects.requireNonNull(message);

        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(message);

        Objects.requireNonNull(accessor.getSessionId());

        this.session = accessor.getSessionId();
        this.subscription = accessor.getSubscriptionId();
        this.destination = accessor.getDestination();

        if(principal instanceof SecurityUser){
            // Connect 프레임에서 Authentication 을 principal 에 세팅함.
            Authentication authToken = (Authentication) principal;
            this.user = (SecurityUser) authToken.getPrincipal();
        } else
            this.user = null;
    }

    // getter
    public String session(){ return session; }
    public String subscription(){ return subscription; }
    public String destination(){ return destination; }
    public SecurityUser user(){ return user; }

    // setter
    public String destination(String destination){
        this.destination = destination;
        return this.destination;
    }

}
