package com.codeit.sb13.playlist_for_everyone.global.websocket.watching.service.chatting;

import com.codeit.sb13.playlist_for_everyone.global.websocket.entity.WSSubscription;

import java.util.List;
import java.util.UUID;

public interface ChattingSessionService {

    void join(WSSubscription subscription, UUID contentId);

    void leave(WSSubscription subscription, UUID contentId);

    void leaveAll(List<WSSubscription> subscriptions, UUID contentId);

    List<WSSubscription> destroy(UUID contentId);

}
