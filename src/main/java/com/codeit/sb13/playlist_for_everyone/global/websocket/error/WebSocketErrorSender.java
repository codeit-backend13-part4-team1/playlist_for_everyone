package com.codeit.sb13.playlist_for_everyone.global.websocket.error;

/**
 * 예외를 공통 오류 본문으로 바꾸어 요청한 WebSocket 세션에 ERROR 프레임으로 전달하는 계약이다.
 * 대화방 브로드캐스트와 별개이며, sessionId는 로그인 세션 ID가 아닌 실제 WebSocket 세션 ID다.
 */
public interface WebSocketErrorSender {

    void sendToSession(String sessionId, Throwable exception);
}
