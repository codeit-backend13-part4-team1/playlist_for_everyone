package com.codeit.sb13.playlist_for_everyone.global.websocket.watching.service;

import com.codeit.sb13.playlist_for_everyone.domain.auth.security.SecurityUser;

import java.util.UUID;

public interface WatchingSessionService {

    void joinViewerSession(String sessionId, String subscriptionId, String destination, SecurityUser auth);
    void leaveViewerSession(String sessionId, String subscriptionId, SecurityUser auth);
    void joinChattingSession(String sessionId, String subscriptionId, String destination, SecurityUser auth);
    void leaveChattingSession(String sessionId, String subscriptionId, SecurityUser auth);
    void cleanUpWithSessionId(String sessionId);
    void cleanUpWithContentId(UUID contentId);
    void publishContentDelete(UUID contentId);
}
