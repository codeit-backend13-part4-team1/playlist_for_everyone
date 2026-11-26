package com.codeit.sb13.playlist_for_everyone.global.websocket.watching.service;

import com.codeit.sb13.playlist_for_everyone.domain.auth.security.SecurityUser;

import java.util.UUID;

public interface WatchingSessionService {

    void joinViewerSession(String sessionId, String subscriptionId, String destination, SecurityUser auth);
    void leaveViewerSession(String sessionId, String subscriptionId, SecurityUser auth);
    void joinChattingSession(String sessionId, String subscriptionId, String destination, SecurityUser auth);
    void leaveChattingSession(String sessionId, String subscriptionId, SecurityUser auth);
    void sendChatMessage(UUID contentId, UUID userId, String message);
    void cleanUpWithSessionId(String sessionId);
    void cleanUpWithContentId(UUID contentId);
    /**
     * 인자로 받은 세션 id 에 대해 세션 등록 수명을 갱싢하는 매서드
     * @param sessionId 세션 id
     */
    void refresh(UUID sessionId);
}
