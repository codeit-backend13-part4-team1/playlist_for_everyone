package com.codeit.sb13.playlist_for_everyone.global.websocket.watching.service;

import jakarta.annotation.Nullable;

public interface WatchingSessionService {
    void subscribeWatchingSession(String sessionId, String subscribe, String content);
    void unsubscribeWatchingSession(String sessionId, @Nullable String subscribe);
    void subscribeChatSession(String sessionId, String subscribe, String content);
    void unsubscribeChatSession(String sessionId, @Nullable String subscribe);
    void sendChat(String sessionId, String subscribe, String content);
}
