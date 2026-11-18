package com.codeit.sb13.playlist_for_everyone.global.websocket.entity;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * 구독 정보를 구체화 한 클래스.
 * 각 구독을 하나의 연결로 감안하는 uuid 와 생성 시간을 저장한다.
 * 연결 해제를 할 시, 사용할 수 있는 콜백 함수를 저장한다.
 */
public class WSSubscription {
    private final String sessionId;
    private final String id;
    private final String destination;

    // 객체 고유 정보.
    private final UUID uuid;
    private final Instant ctime;

    /**
     * 구독 생성자
     * @param id Spring 에서 생성하는 구독 id.
     * @param destination Spring 에서 생성하는 구독 destination.
     */
    public WSSubscription(String sessionId, String id, String destination) {
        this.sessionId = Objects.requireNonNull(sessionId);
        this.id = Objects.requireNonNull(id);
        this.destination = Objects.requireNonNull(destination);
        this.uuid = UUID.randomUUID();
        this.ctime = Instant.now();
    }

    public String id(){
        return this.id;
    }

    public String sessionId(){ return this.sessionId; }

    public UUID uuid(){
        return this.uuid;
    }

    public Instant createdAt(){
        return this.ctime;
    }

    public String destination(){
        return this.destination;
    }

}
