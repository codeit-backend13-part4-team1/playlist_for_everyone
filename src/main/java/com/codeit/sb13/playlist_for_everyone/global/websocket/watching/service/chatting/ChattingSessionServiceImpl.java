package com.codeit.sb13.playlist_for_everyone.global.websocket.watching.service.chatting;

import com.codeit.sb13.playlist_for_everyone.global.exception.websocket.WebSocketSessionNotFoundException;
import com.codeit.sb13.playlist_for_everyone.global.service.redis.RedisTopicRegistry;
import com.codeit.sb13.playlist_for_everyone.global.websocket.watching.ChatSessionMessageListener;
import com.codeit.sb13.playlist_for_everyone.global.websocket.watching.entity.ChattingSessionRoom;
import com.codeit.sb13.playlist_for_everyone.global.websocket.watching.repository.chatting.ChattingSessionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class ChattingSessionServiceImpl implements ChattingSessionService {

    private final ChattingSessionRepository chattingSessionRepository;
    private final RedisTopicRegistry redisTopicRegistry;
    private final ChatSessionMessageListener chatSessionMessageListener;


    @Override
    public void join(String sessionId, String subscribeId, UUID contentId) {
        Objects.requireNonNull(sessionId);
        Objects.requireNonNull(subscribeId);
        Objects.requireNonNull(contentId);
        ChattingSessionRoom room;

        synchronized (room = chattingSessionRepository.findOrCreate(contentId)) {
            redisTopicRegistry.register(room.topic(), sessionId, subscribeId, chatSessionMessageListener);
        }
    }

    @Override
    public void leave(String sessionId, String subscribeId, UUID contentId) {
        Objects.requireNonNull(sessionId);
        Objects.requireNonNull(subscribeId);
        Objects.requireNonNull(contentId);
        ChattingSessionRoom room = chattingSessionRepository.find(contentId)
                .orElseThrow(WebSocketSessionNotFoundException::new);

        synchronized (room) {
            redisTopicRegistry.unregister(sessionId, subscribeId);
        }
    }

    @Override
    public void leaveAll(String sessionId, List<String> subscriptionIds, UUID contentId) {
        Objects.requireNonNull(sessionId);
        Objects.requireNonNull(contentId);
        ChattingSessionRoom room = chattingSessionRepository.find(contentId)
                .orElseThrow(WebSocketSessionNotFoundException::new);

        synchronized (room) {
            // 해당 구독을 한번제 저장소에서 지울 수 있도록
            redisTopicRegistry.unregisterAll(sessionId, subscriptionIds);
        }
    }
}
