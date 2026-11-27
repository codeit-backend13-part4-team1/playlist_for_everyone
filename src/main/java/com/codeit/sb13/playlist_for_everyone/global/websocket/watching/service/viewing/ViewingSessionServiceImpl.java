package com.codeit.sb13.playlist_for_everyone.global.websocket.watching.service.viewing;

import com.codeit.sb13.playlist_for_everyone.global.exception.websocket.WebSocketSessionRegisterException;
import com.codeit.sb13.playlist_for_everyone.global.service.lock.Locks;
import com.codeit.sb13.playlist_for_everyone.global.service.redis.RedisTopicRegistry;
import com.codeit.sb13.playlist_for_everyone.global.websocket.entity.WSSubscription;
import com.codeit.sb13.playlist_for_everyone.global.websocket.watching.ViewSessionMessageListener;
import com.codeit.sb13.playlist_for_everyone.global.websocket.watching.dto.ViewingSessionInfoDto;
import com.codeit.sb13.playlist_for_everyone.global.websocket.watching.entity.ViewingSessionRoom;
import com.codeit.sb13.playlist_for_everyone.global.websocket.watching.repository.ViewingSessionRedisRepository;
import com.codeit.sb13.playlist_for_everyone.global.websocket.watching.repository.viewing.ViewingSessionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 컨텐츠에 대한 구독을 관리하는 클래스 구현체
 * 컨텐츠에 대한 등록, 삭제의 직렬화 및 각 룸의 정보에 대한 조회를 담당한다.
 */
@Component
@RequiredArgsConstructor
public class ViewingSessionServiceImpl implements ViewingSessionService{
    private final ViewingSessionRepository viewingSessionRepository;
    private final ViewSessionMessageListener viewSessionMessageListener;
    private final ViewingSessionRedisRepository viewingSessionPresence;
    private final RedisTopicRegistry redisTopicRegistry;
    private final RedisTemplate<String, Object> redisTemplate;
    private final Locks locks;

    @Override
    public boolean join(WSSubscription subscription, UUID contentId, UUID userId) {
        // 1. 인자 체크
        Objects.requireNonNull(subscription);
        Objects.requireNonNull(contentId);

        boolean fresh_user = false;

        // 2. 룸에 대한 락 획득
        synchronized (locks.lock(contentId)) {
            ViewingSessionRoom room = viewingSessionRepository.findOrCreate(contentId);
            // 3. 세션 룸에 구독 등록
            // 글로벌
            fresh_user = viewingSessionPresence.register(
                    subscription.uuid(),
                    contentId,
                    userId,
                    subscription.createdAt()
            );
            // 4. 메세지 수신을 위한 브로커 등록 -> 단일이라면 redis 등록이 없어진다.
            // todo - UUID 등록으로 간편하게 되도록 변경
            redisTopicRegistry.register(
                    room.topic(),
                    subscription.sessionId(),
                    subscription.id(),
                    viewSessionMessageListener);

            room.join(subscription);
        }
        return fresh_user;
    }

    @Override
    public void leave(WSSubscription subscription, UUID contentId) {
        Objects.requireNonNull(subscription);
        Objects.requireNonNull(contentId);

        synchronized (locks.lock(contentId)) {
            ViewingSessionRoom room = getRoomOrException(contentId);

            room.leave(subscription);
            viewingSessionPresence.unregister(subscription.uuid());
            redisTopicRegistry.unregister(subscription.sessionId(), subscription.id());
        }
    }

    @Override
    public void leaveAll(List<WSSubscription> subscriptions, UUID contentId) {
        Objects.requireNonNull(contentId);

        /* content 단위 락은 외부에서 걸어서, 다중작업을 한번에 할 수 있도록 변경. */

        ViewingSessionRoom room = getRoomOrException(contentId);
        room.leaveAll(subscriptions);

        // tmp
        String sessionId = subscriptions.get(0).sessionId();
        List<String> subscriptionIds = subscriptions.stream().map(WSSubscription::id).collect(Collectors.toList());
        List<UUID> subscriptionKeys = subscriptions.stream().map(WSSubscription::uuid).toList();
        // todo. - 글로벌, topic 저장소를 UUID 기준으로 변경하면서 변경.
        viewingSessionPresence.unregisterAll(subscriptionKeys);
        redisTopicRegistry.unregisterAll(sessionId, subscriptionIds);
    }

    // 여러 인스턴스에서 동일 로직 수행 시, 로직 사이에 동일 id 로 세션 등록이 들어오면, 생성한 등록을 삭제 해 버릴 가능성 있음.
    // todo - 추후 로컬에 등록된 세션만 삭제 하도록 변경 예정. 해당 저장소 및 메세징 채널 등록을
    //  repository 레이어에 합친다. 이후, lock 도 레파지토리 레이어로 넘겨서 spread 가 아닌 각 room 별로 할 수 있도록 변경.
    @Override
    public List<WSSubscription> destroy(UUID contentId) {
        List<WSSubscription> res;

        try {
            ViewingSessionRoom room = getRoomOrException(contentId);
            // 저장소에서 세션 해지 및, 토픽 등록 해제.
            viewingSessionPresence.deleteWithContentId(contentId);
            redisTopicRegistry.disconnectWithContentId(contentId.toString());
            // 실제 리소스 삭제.
            viewingSessionRepository.remove(room);

            // 반환용 로컬 구독 리스트 사본 생성
            res = room.getAll();
            room.clear();
            return res;
        } catch (WebSocketSessionRegisterException e) {
            return List.of();
        }
    }

    @Override
    public void send(UUID contentId, Object payload){
        ViewingSessionRoom room = getRoomOrException(contentId);
        redisTemplate.convertAndSend(room.topic(), payload);
    }

    @Override
    public void refresh(List<UUID> subscriptionKeys) {
        viewingSessionPresence.refresh(subscriptionKeys);
    }

    @Override
    public long getCurrentViewerCount(UUID contentId) {
        return viewingSessionPresence.getWatcherCount(contentId.toString());
    }

    @Override
    public Map<UUID, Long> getCurrentViewerCountWithContents(List<UUID> contentIdList) {
        return viewingSessionPresence.getWatcherCountWithMultipleContents(
                contentIdList.stream()
                        .map(UUID::toString)
                        .toList());
    }

    @Override
    public List<ViewingSessionInfoDto> getCurrentViewerIdList(UUID contentId) {
        return viewingSessionPresence.currentViewerIdList(contentId.toString());
    }


    @Override
    public Optional<ViewingSessionInfoDto> queryViewerId(UUID userId) {
        return viewingSessionPresence.queryViewerId(userId.toString());
    }

    private ViewingSessionRoom getRoomOrException(UUID contentId){
        return viewingSessionRepository.find(contentId)
                .orElseThrow(WebSocketSessionRegisterException::new);
    }
}
