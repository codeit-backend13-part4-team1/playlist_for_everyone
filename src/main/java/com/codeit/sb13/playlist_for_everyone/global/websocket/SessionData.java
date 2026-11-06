package com.codeit.sb13.playlist_for_everyone.global.websocket;

import com.codeit.sb13.playlist_for_everyone.domain.auth.security.SecurityUser;
import jakarta.annotation.Nullable;
import lombok.Builder;
import org.springframework.security.core.Authentication;

import java.security.Principal;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;


/*
 * 세션 - 구독 키 쌍을 하나의 클래스로 관리하기 위한 클래스.
 */

public class SessionData {

    private final UUID id;
    private final Instant createdAt;
    private final String sessionId;
    @Nullable private final String subscriptionId;
    @Nullable private final String destination;
    @Nullable private final Principal principal;

    @Builder
    public SessionData(
            String session,
            @Nullable String subscription,
            @Nullable String destination,
            @Nullable Principal principal
    ){
        this.id = UUID.randomUUID();
        this.createdAt = Instant.now();
        this.sessionId = Objects.requireNonNull(session);

        this.subscriptionId = subscription;
        this.destination = destination;
        this.principal = principal;
    }

    // 내부 매서드용 생성자.
    private SessionData(
            UUID id,
            Instant instant,
            String session,
            @Nullable String subscription,
            @Nullable String destination,
            @Nullable Principal principal
    ) {
        this.id = id;
        this.createdAt = instant;
        this.sessionId = Objects.requireNonNull(session);
        this.subscriptionId = subscription;
        this.destination = destination;
        this.principal = principal;
    }

    public UUID id() {
        return id;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public String session(){
        return sessionId;
    }

    public String subscription(){
        return subscriptionId;
    }

    public String destination() { return destination; }

    public Principal principal() {
        return principal;
    }

    public SecurityUser user() {
        if (principal instanceof Authentication authentication)
            return (SecurityUser) authentication.getPrincipal();
        return null;
    }

    static public SessionData copyOf(SessionData object){
        Objects.requireNonNull(object);
        return new SessionData(
                object.id(),
                object.createdAt(),
                object.session(),
                object.subscription(),
                object.destination(),
                object.principal()
        );
    }


    // equals 와 hashCode 는 sessionId 와 subscriptionId 를 기준으로 구현한다.
    @Override
    public int hashCode() {
        return Objects.hash(sessionId, subscriptionId);
    }

    @Override
    public boolean equals(Object obj) {
        if(this == obj) return true;
        if(!(obj instanceof SessionData connection)) return false;
        return Objects.equals(sessionId, connection.sessionId) && Objects.equals(subscriptionId, connection.subscriptionId);
    }
}
