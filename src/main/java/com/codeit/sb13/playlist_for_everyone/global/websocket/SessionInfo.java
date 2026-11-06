package com.codeit.sb13.playlist_for_everyone.global.websocket;

import com.codeit.sb13.playlist_for_everyone.domain.auth.security.SecurityUser;
import jakarta.annotation.Nullable;
import org.springframework.messaging.Message;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.security.core.Authentication;

import java.security.Principal;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * 웹 소켓 메세지에서 세션 및 사용자 정보를 추출하는 클래스.
 * message 에서 확인 가능한 정보를 추출 한 것이기 때문에, registry 에 저장된
 * SessionData 와는 차이가 있을 수 있다.
 */

public class SessionInfo {
    // 불변 항목
    private final String session;
    private final String subscription;
    // wrap 항목
    private UUID id;
    private Instant ctime;
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
        this.id = null;
        this.ctime = null;
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
        this.id = null;
        this.ctime = null;
    }

    private SessionInfo (
            String session,
            String subscription,
            UUID id,
            Instant createdAt,
            String destination,
            Principal principal
    ) {
        this.session = session;
        this.subscription = subscription;
        this.id = id;
        this.ctime = createdAt;
        this.destination = destination;
        this.principal = principal;
    }


    // getter
    @Nullable public String session(){ return session; }
    @Nullable public String subscription(){ return subscription; }
    @Nullable public UUID id(){ return id; }
    @Nullable public Instant createdAt(){ return ctime; }
    @Nullable public String destination(){ return destination; }
    @Nullable public Principal principal(){ return principal; }
    @Nullable public SecurityUser user(){
        if (principal instanceof Authentication authentication) {
            return (SecurityUser) authentication.getPrincipal();
        } else
            return null;
    }

    // setter
    @Nullable public SessionInfo destination(String destination){
        if (destination != null) this.destination = destination;
        return this;
    }

    // 편의 매서드
    public SessionInfo wrap(SessionData data){
        this.id = data.id();
        this.ctime = data.createdAt();
        this.destination = data.destination();
        this.principal = data.principal();
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

    static public SessionInfo fromData(SessionData data){
        return new SessionInfo(
                data.session(),
                data.subscription(),
                data.id(),
                data.createdAt(),
                data.destination(),
                data.principal()
        );
    }

}
