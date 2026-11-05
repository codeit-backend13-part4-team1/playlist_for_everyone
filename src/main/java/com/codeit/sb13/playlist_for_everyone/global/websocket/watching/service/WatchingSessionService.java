package com.codeit.sb13.playlist_for_everyone.global.websocket.watching.service;

import com.codeit.sb13.playlist_for_everyone.global.websocket.SessionInfo;

import java.util.UUID;

public interface WatchingSessionService {

    /**
     * 현재 컨텐츠의 시청중인 동시 시청자 수를 반환하는 매서드
     * @param contentId 컨텐츠 엔티티의 PK 가 되는 키값
     * @return 현재 시청중인 시청자 수
     */
    long getCurrentViewerCount(UUID contentId);

    /**
     * 동시 시청 세션의 redis topic 이 될 문자열 키를 반환하는 매서드.
     * @param info 세션 정보가 포함된 객체
     * @return 토픽 문자열
     */
    String topic(SessionInfo info);

    /**
     * 동시 시청의 시청 세션에 대해 사용자 세션을 등록하는 매서드.
     * @param info 세션 정보가 포함된 객체
     */
    void joinViewerSession(SessionInfo info);

    /**
     * 동시 시청 세션에 대해 사용자 세션을 등록 취소 하는 매서드.
     * @param info 세션 정보가 포함된 객체
     */
    void leaveViewerSession(SessionInfo info);

    /**
     * 동시 시청 채팅 세션에 대해 사용자 세션을 등록하는 매서드.
     * @param info 세션 정보가 포함된 객체
     */
    void joinChatSession(SessionInfo info);

    /**
     * 동시 시청 채팅 세션에 대해 사용자 세션을 등록 취소 하는 매서드.
     * @param info 세션 정보가 포함된 객체
     */
    void leaveChatSession(SessionInfo info);
}
