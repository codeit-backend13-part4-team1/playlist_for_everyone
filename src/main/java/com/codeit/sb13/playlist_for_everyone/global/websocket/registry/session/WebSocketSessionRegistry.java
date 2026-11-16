package com.codeit.sb13.playlist_for_everyone.global.websocket.registry.session;


import com.codeit.sb13.playlist_for_everyone.global.websocket.entity.WSSession;

import java.util.List;
import java.util.Optional;

/**
 * raw 웹 소켓(Session)을 등록하는 인터페이스
 * Spring 에서 등록하는 sessionID 를 기반으로 세션을 등록한다.
 */
public interface WebSocketSessionRegistry {

    /**
     * 세션을 레지스트리에 등록한다.
     * @param session WebSocketSession 자체
     */
    void register(WSSession session);

    /**
     * 등록된 세션을 레지스트리에서 삭제한다.
     * @param id 세션 Id
     */
    void unregister(String id);

    /**
     * sessionId 에 해당하는 세션을 조회한다.
     * @param sessionId 세션 id
     * @return WebSession 의 옵셔널 객체
     */
    Optional<WSSession> query(String sessionId);

    /**
     * sessionId 리스트에 해당하는 세션들을 조회한다.
     * @param sessionIdList 세션 id 리스트
     * @return WebSession 리스트
     */
    List<WSSession> queryAll(List<String> sessionIdList);

}
