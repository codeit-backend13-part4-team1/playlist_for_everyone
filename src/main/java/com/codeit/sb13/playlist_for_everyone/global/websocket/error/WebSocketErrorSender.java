package com.codeit.sb13.playlist_for_everyone.global.websocket.error;

public interface WebSocketErrorSender {

    void sendToSession(String sessionId, Throwable exception);
}
