package com.codeit.sb13.playlist_for_everyone.global.websocket.event;

/**
 * 웹 소켓 관련해서 정의하는 이벤트 객체.
 */

public interface WebSocketEvent {
    /**
     * 이벤트를 발행한다.
     */
    void publish(Object data);
}
