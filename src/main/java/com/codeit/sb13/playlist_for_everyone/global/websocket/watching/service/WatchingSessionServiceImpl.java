package com.codeit.sb13.playlist_for_everyone.global.websocket.watching.service;

import com.codeit.sb13.playlist_for_everyone.domain.content.service.ContentService;
import com.codeit.sb13.playlist_for_everyone.domain.user.service.UserService;
import com.codeit.sb13.playlist_for_everyone.global.service.RedisTopicRegistry;
import com.codeit.sb13.playlist_for_everyone.global.websocket.ConnectionRegistry;
import com.codeit.sb13.playlist_for_everyone.global.websocket.service.WatcherRegistry;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class WatchingSessionServiceImpl implements WatchingSessionService {

    private final ConnectionRegistry connectionRegistry;
    private final WatcherRegistry watcherRegistry;
    private final RedisTopicRegistry redisTopicRegistry;
    private final UserService userService;
    private final ContentService contentService;

    @Override
    public void subscribeWatchingSession(String sessionId, String subscribe, String content) {

    }

    @Override
    public void unsubscribeWatchingSession(String sessionId, @Nullable String subscribe) {

    }

    @Override
    public void subscribeChatSession(String sessionId, String subscribe, String content) {

    }

    @Override
    public void unsubscribeChatSession(String sessionId, @Nullable String subscribe) {

    }

    @Override
    public void sendChat(String sessionId, String subscribe, String content) {

    }
}
