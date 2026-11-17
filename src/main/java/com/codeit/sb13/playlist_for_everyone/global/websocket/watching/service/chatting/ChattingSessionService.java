package com.codeit.sb13.playlist_for_everyone.global.websocket.watching.service.chatting;

import java.util.UUID;

public interface ChattingSessionService {

    void join(String sessionId, String subscribeId, UUID contentId);

    void leave(String sessionId, String subscribeId, UUID contentId);

    void leaveAll(String sessionId);

}
