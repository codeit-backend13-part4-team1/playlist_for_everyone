package com.codeit.sb13.playlist_for_everyone.global.websocket.registry.session;


import org.springframework.web.socket.WebSocketSession;

import java.util.Optional;

/**
 * raw 웹 소켓(Session)을 등록하는 인터페이스
 * Spring 에서 등록하는 sessionID 를 기반으로 세션을 등록한다.
 */
public interface WebSocketRegistry {

    /**
     * 세션을 레지스트리에 등록한다.
     * @param session WebSocketSession 자체
     */
    void register(WebSocketSession session);

    /**
     * 등록된 세션을 레지스트리에서 삭제한다.
     * @param session 세션
     */
    void unregister(WebSocketSession session);

    /**
     * sessionId 에 해당하는 세션을 조회한다.
     * @param sessionId 세션 id
     * @return WebSocketSession 의 옵셔널 객체
     */
    Optional<WebSocketSession> query(String sessionId);

    /**
     * sessionId 에 해당하는 세션을 종료한다,.
     * @param sessionId 세션ID
     * @throws Exception IOException 이 반환된다.
     */
    void close(String sessionId) throws Exception;

}
