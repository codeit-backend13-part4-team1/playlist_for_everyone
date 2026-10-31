package com.codeit.sb13.playlist_for_everyone.global.websocket;

import org.springframework.security.core.Authentication;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;


/*
 * 세션 - 구독 키 쌍을 하나의 클래스로 관리하기 위한 클래스.
 */
public class SocketConnection {

    private final UUID id;
    private final Instant createdAt;
    private final String sessionId;
    private final String subscriptionId;
    private final Authentication authentication;

    public SocketConnection(
            String sessionId,
            String subscriptionId,
            Authentication authentication
    ) {
        // sessionId 는 null 일 수 없음.
        Objects.requireNonNull(sessionId);

        this.id = UUID.randomUUID();
        this.createdAt = Instant.now();
        this.sessionId = sessionId;
        this.subscriptionId = subscriptionId;
        this.authentication = authentication;
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

    /*
     * Authentication 의 principal 을 쓰게 되었으므로 편의적으로 반환 한다.
     */
    public Object principal() {
        return authentication.getPrincipal();
    }

    // equals 와 hashCode 는 sessionId 와 subscriptionId 를 기준으로 구현한다.
    @Override
    public int hashCode() {
        return Objects.hash(sessionId, subscriptionId);
    }

    @Override
    public boolean equals(Object obj) {
        if(this == obj) return true;
        if(!(obj instanceof SocketConnection connection)) return false;
        return Objects.equals(sessionId, connection.sessionId) && Objects.equals(subscriptionId, connection.subscriptionId);
    }
}
