package com.codeit.sb13.playlist_for_everyone.global.websocket.service;

import java.util.UUID;

/**
 * 웹 소켓 세션의 기본적인 관 매서드를 제공하는 인터페이스
 */
public interface WebSocketService {

    /**
     * 유저 로그아웃 이벤트에서, 유저 정보를 받아와서
     * 해당 인증정보로 연결된 세션을 닫는 매서드.
     * 해당 세션을 닫기만 하고, 비즈니스 로직에 대한 로직은 세션 연결 끊기에 의해 일어나는 후속 이벤트에서 처리한다.
     */
    void disconnectWithAuthRevokeEvent(UUID userId, UUID sessionId);

}
