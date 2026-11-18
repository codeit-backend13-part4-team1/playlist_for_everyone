package com.codeit.sb13.playlist_for_everyone.global.websocket.watching.service.chatting;

import com.codeit.sb13.playlist_for_everyone.global.exception.websocket.WebSocketSessionNotFoundException;
import com.codeit.sb13.playlist_for_everyone.global.service.lock.Locks;
import com.codeit.sb13.playlist_for_everyone.global.service.redis.RedisTopicRegistry;
import com.codeit.sb13.playlist_for_everyone.global.websocket.entity.WSSubscription;
import com.codeit.sb13.playlist_for_everyone.global.websocket.watching.ChatSessionMessageListener;
import com.codeit.sb13.playlist_for_everyone.global.websocket.watching.entity.ChattingSessionRoom;
import com.codeit.sb13.playlist_for_everyone.global.websocket.watching.repository.chatting.ChattingSessionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class ChattingSessionServiceImpl implements ChattingSessionService {

    private final ChattingSessionRepository chattingSessionRepository;
    private final RedisTopicRegistry redisTopicRegistry;
    private final ChatSessionMessageListener chatSessionMessageListener;
    private final Locks locks;


    @Override
    public void join(WSSubscription subscription, UUID contentId) {
        Objects.requireNonNull(subscription);
        Objects.requireNonNull(contentId);

        synchronized (locks.lock(contentId)) {
            ChattingSessionRoom room = chattingSessionRepository.findOrCreate(contentId);
            room.join(subscription);
            redisTopicRegistry.register(
                    room.topic(),
                    subscription.sessionId(),
                    subscription.id(),
                    chatSessionMessageListener);
        }
    }

    @Override
    public void leave(WSSubscription subscription, UUID contentId) {
        Objects.requireNonNull(subscription);
        Objects.requireNonNull(contentId);


        synchronized (locks.lock(contentId)) {
            ChattingSessionRoom room = chattingSessionRepository.find(contentId)
                    .orElseThrow(WebSocketSessionNotFoundException::new);
            room.leave(subscription);
            redisTopicRegistry.unregister(
                    subscription.sessionId(),
                    subscription.id());
        }
    }

    @Override
    public void leaveAll(List<WSSubscription> subscriptions, UUID contentId) {
        Objects.requireNonNull(contentId);


        synchronized (locks.lock(contentId)) {
            ChattingSessionRoom room = chattingSessionRepository.find(contentId)
                    .orElseThrow(WebSocketSessionNotFoundException::new);
            // 해당 구독을 한번제 저장소에서 지울 수 있도록
            room.leaveAll(subscriptions);

            //tmp
            String sessionId = subscriptions.get(0).sessionId();
            List<String> subscriptionIds = subscriptions.stream().map(WSSubscription::id).collect(Collectors.toList());
            redisTopicRegistry.unregisterAll(sessionId, subscriptionIds);
        }
    }

    // 여러 인스턴스에서 동일 로직 수행 시, 로직 사이에 동일 id 로 세션 등록이 들어오면, 생성한 등록을 삭제 해 버릴 가능성 있음.
    @Override
    public List<WSSubscription> destroy(UUID contentId) {
        List<WSSubscription> res;

        synchronized (locks.lock(contentId)) {
            ChattingSessionRoom room = chattingSessionRepository.find(contentId)
                    .orElseThrow(WebSocketSessionNotFoundException::new);
            // 저장소에서 세션 해지 및, 토픽 등록 해제.
            redisTopicRegistry.disconnectWithContentId(contentId.toString());

            res = room.getAll();
            room.clear();
            return res;
        }
    }
}
