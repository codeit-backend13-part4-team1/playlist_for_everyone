package com.codeit.sb13.playlist_for_everyone.global.websocket.watching.service.viewing;

import com.codeit.sb13.playlist_for_everyone.global.exception.websocket.WebSocketSessionNotFoundException;
import com.codeit.sb13.playlist_for_everyone.global.exception.websocket.WebSocketSessionRegisterException;
import com.codeit.sb13.playlist_for_everyone.global.service.redis.RedisTopicRegistry;
import com.codeit.sb13.playlist_for_everyone.global.websocket.watching.ViewSessionMessageListener;
import com.codeit.sb13.playlist_for_everyone.global.websocket.watching.entity.ViewingSessionRoom;
import com.codeit.sb13.playlist_for_everyone.global.websocket.watching.repository.WatchingSessionRepository;
import com.codeit.sb13.playlist_for_everyone.global.websocket.watching.repository.viewing.ViewingSessionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * 컨텐츠에 대한 구독을 관리하는 클래스 구현체
 * 컨텐츠에 대한 등록, 삭제의 직렬화 및 각 룸의 정보에 대한 조회를 담당한다.
 */
@Component
@RequiredArgsConstructor
public class ViewingSessionServiceImpl implements ViewingSessionService{
    private final ViewingSessionRepository viewingSessionRepository;
    private final ViewSessionMessageListener viewSessionMessageListener;
    private final WatchingSessionRepository viewingSessionPresence;
    private final RedisTopicRegistry redisTopicRegistry;

    private final RedisTemplate<String, Object> redisTemplate;

    @Override
    public void join(String sessionId, String subscribeId, UUID contentId) {
        // 1. 인자 체크
        Objects.requireNonNull(sessionId);
        Objects.requireNonNull(subscribeId);
        Objects.requireNonNull(contentId);
        ViewingSessionRoom room;

        // 2. 룸에 대한 락 획득
        synchronized (room = viewingSessionRepository.findOrCreate(contentId)) {
            // 3. 세션 룸에 구독 등록
            viewingSessionPresence.register(sessionId, subscribeId, contentId.toString());
            // 4. 메세지 수신을 위한 브로커 등록 -> 단일이라면 redis 등록이 없어진다.
            redisTopicRegistry.register(room.topic(), sessionId, subscribeId, viewSessionMessageListener);
        }
    }

    @Override
    public void leave(String sessionId, String subscribeId, UUID contentId) {
        Objects.requireNonNull(sessionId);
        Objects.requireNonNull(subscribeId);
        Objects.requireNonNull(contentId);

        // 룸이 없다면 예외 발생.
        ViewingSessionRoom room = viewingSessionRepository.find(contentId)
                .orElseThrow(WebSocketSessionRegisterException::new);

        synchronized (room) {
            viewingSessionPresence.unregister(sessionId, subscribeId);
            redisTopicRegistry.unregister(sessionId, subscribeId);
        }
    }

    @Override
    public void leaveAll(String sessionId, List<String> subscriptionIds, UUID contentId) {
        Objects.requireNonNull(sessionId);
        Objects.requireNonNull(contentId);
        ViewingSessionRoom room = viewingSessionRepository.find(contentId)
                .orElseThrow(WebSocketSessionNotFoundException::new);

        synchronized (room) {
            // 해당 구독을 한번제 저장소에서 지울 수 있도록
            viewingSessionPresence.unregisterAll(sessionId,subscriptionIds, contentId);
            redisTopicRegistry.unregisterAll(sessionId, subscriptionIds);
        }
    }



    @Override
    public void send(UUID contentId, Object payload){
        ViewingSessionRoom room = viewingSessionRepository.find(contentId)
                .orElseThrow(WebSocketSessionRegisterException::new);
        redisTemplate.convertAndSend(room.topic(), payload);
    }

    @Override
    public void refresh(String sessionId) {
        viewingSessionPresence.refresh(sessionId);
    }

    @Override
    public long getCurrentViewerCount(UUID contentId) {
        return viewingSessionPresence.getWatcherCount(contentId.toString());
    }

    @Override
    public Map<UUID, Long> getCurrentViewerCountMap(List<UUID> contentIdList) {
        return viewingSessionPresence.getWatcherCountWithMultipleContents(
                contentIdList.stream()
                        .map(UUID::toString)
                        .toList());
    }
}
