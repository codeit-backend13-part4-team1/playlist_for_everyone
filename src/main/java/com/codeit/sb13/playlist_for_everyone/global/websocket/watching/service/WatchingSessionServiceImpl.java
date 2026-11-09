package com.codeit.sb13.playlist_for_everyone.global.websocket.watching.service;

import com.codeit.sb13.playlist_for_everyone.domain.auth.security.SecurityUser;
import com.codeit.sb13.playlist_for_everyone.global.exception.websocket.WebSocketAuthInvalidException;
import com.codeit.sb13.playlist_for_everyone.global.exception.websocket.WebSocketDestinationInvalidException;
import com.codeit.sb13.playlist_for_everyone.global.service.MatchRule;
import com.codeit.sb13.playlist_for_everyone.global.service.redis.RedisTopicRegistry;
import com.codeit.sb13.playlist_for_everyone.global.websocket.SessionInfo;
import com.codeit.sb13.playlist_for_everyone.global.websocket.watching.ChatSessionMessageListener;
import com.codeit.sb13.playlist_for_everyone.global.websocket.watching.ViewSessionMessageListener;
import com.codeit.sb13.playlist_for_everyone.global.websocket.watching.repository.WatchingSessionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

import static com.codeit.sb13.playlist_for_everyone.global.websocket.watching.config.WatchingSessionProperties.*;
import static com.codeit.sb13.playlist_for_everyone.global.websocket.watching.WebSocketUtils.check;

@Component
@RequiredArgsConstructor
public class WatchingSessionServiceImpl implements WatchingSessionService {

    private final WatchingSessionRepository watchingSessionRepository;
    private final RedisTopicRegistry topicRegistry;
    private final ViewSessionMessageListener viewSessionMessageListener;
    private final ChatSessionMessageListener chatSessionMessageListener;

    enum WatchingSessionType {
        VIEW,
        CHAT,
        DEFAULT
    }

    @Override
    public long getCurrentViewerCount(UUID contentId){
        return watchingSessionRepository.getWatcherCount(contentId.toString());
    }


    @Override
    public List<String> disconnectWithContentId(UUID contentId){
//        return watchingSessionRepository.disconnectWithContentId(contentId.toString());
        return null;
    }


    /**
     * 세션 경로에 따라 채팅, 혹은 뷰어 세션을 분간하고,
     * 해당 세션의 메세지 토픽을 반환한다.
     * @param info 세션 정보가 포함된 객체
     * @return redis 메세지 퍼블리싱 용 토픽.
     * 컨텐츠 별로 구분된 토픽을 반환한다.
     */
    @Override
    public String topic(SessionInfo info) {
        String destination = check(info.destination());

        WatchingSessionType type = WatchingSessionType.DEFAULT;

        if (MatchRule.check(destination,watchingSessionUrlPattern())) type = WatchingSessionType.VIEW;
        if (MatchRule.check(destination,watchingChatUrlPattern())) type = WatchingSessionType.CHAT;

        return switch (type) {
            case VIEW -> merge(MESSAGE_VIEW_PREFIX, MatchRule.extract(destination,UUID_REGEX));
            case CHAT -> merge(MESSAGE_CHAT_PREFIX, MatchRule.extract(destination,UUID_REGEX));
            default -> throw new WebSocketDestinationInvalidException(null);
        };
    }

    private String merge(String p , List<String> q){
        if (q.isEmpty()) throw new WebSocketDestinationInvalidException(null);
        return p + q.get(0);
    }

    @Override
    public void refresh(String sessionId){
        watchingSessionRepository.refresh(sessionId);
    }

    @Override
    public void joinViewerSession(SessionInfo info) {
        String session = check(info.session());
        String subscribe = check(info.subscription());
        checkAuth(info);
        destinationCheck(info);

        List<String> extract = MatchRule.extract(info.destination(),UUID_REGEX);
        if (extract.isEmpty()) throw new WebSocketDestinationInvalidException(null);

        String content = extract.get(0);

        String topic = topic(info);

        watchingSessionRepository.register(session,subscribe,content);
        topicRegistry.register(topic, session, subscribe, viewSessionMessageListener);
    }


    @Override
    public void leaveViewerSession(SessionInfo info) {
        String session = check(info.session());
        String subscribe = check(info.subscription());
        destinationCheck(info);

        watchingSessionRepository.unregister(session,subscribe);
        topicRegistry.unregister(session, subscribe);
    }

    @Override
    public void joinChatSession(SessionInfo info) {
        String session = check(info.session());
        String subscribe = check(info.subscription());
        String topic = topic(info);

        topicRegistry.register(topic, session, subscribe, chatSessionMessageListener);
    }

    @Override
    public void leaveChatSession(SessionInfo info) {
        String session = check(info.session());
        String subscribe = info.subscription();

        if (subscribe == null) {
            // 구독 없음 - disconnect
            topicRegistry.disconnect(session);
        } else {
            // 구독 있음 - unsubscribe
            topicRegistry.unregister(session, subscribe);
        }

    }

    private void checkAuth(SessionInfo info){
        SecurityUser user = check(info.user());

        if (!user.isEnabled())
            throw new WebSocketAuthInvalidException();
    }

    private void destinationCheck(SessionInfo info){
        String destination = check(info.destination());
        // chat 이나 view 경로가 아니면 에러 반환.
        if(!(MatchRule.check(destination,watchingSessionUrlPattern())
                || MatchRule.check(destination,watchingChatUrlPattern())))
            throw new WebSocketDestinationInvalidException();
    }

}
