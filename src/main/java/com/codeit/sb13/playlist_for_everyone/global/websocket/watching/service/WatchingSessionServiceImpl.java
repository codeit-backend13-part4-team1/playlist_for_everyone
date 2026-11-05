package com.codeit.sb13.playlist_for_everyone.global.websocket.watching.service;

import com.codeit.sb13.playlist_for_everyone.domain.auth.security.SecurityUser;
import com.codeit.sb13.playlist_for_everyone.global.exception.user.UserNotFoundException;
import com.codeit.sb13.playlist_for_everyone.global.exception.websocket.DestinationInvalidException;
import com.codeit.sb13.playlist_for_everyone.global.service.MatchRule;
import com.codeit.sb13.playlist_for_everyone.global.service.redis.RedisTopicRegistry;
import com.codeit.sb13.playlist_for_everyone.global.websocket.SessionInfo;
import com.codeit.sb13.playlist_for_everyone.global.websocket.ViewSessionMessageListener;
import com.codeit.sb13.playlist_for_everyone.global.websocket.watching.repository.WatchingSessionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

import static com.codeit.sb13.playlist_for_everyone.global.websocket.watching.config.WatchingSessionProperties.*;
import static com.codeit.sb13.playlist_for_everyone.global.service.MatchRule.*;


@Component
@RequiredArgsConstructor
public class WatchingSessionServiceImpl implements WatchingSessionService {

    private final WatchingSessionRepository watchingSessionRepository;
    private final RedisTopicRegistry topicRegistry;
    private final ViewSessionMessageListener viewSessionMessageListener;

    private final MatchRule WATCHING_VIEW_PATTERN = MatchRule.builder()
            .prefix(PATH_PREFIX)
            .pattern(UUID_REGEX)
            .postfix(VIEW_POSTFIX)
            .build();
    private final MatchRule WATCHING_CHAT_PATTERN = MatchRule.builder()
            .prefix(PATH_PREFIX)
            .pattern(UUID_REGEX)
            .postfix(CHAT_POSTFIX)
            .build();

    enum WatchingSessionType {
        VIEW,
        CHAT,
        DEFAULT
    }

    @Override
    public long getCurrentViewerCount(UUID contentId){
        return watchingSessionRepository.getWatcherCount(contentId.toString());
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
        String destination = Objects.requireNonNull(info.destination());
        WatchingSessionType type = WatchingSessionType.DEFAULT;
        if (check(destination,watchingSessionUrlPattern())) type = WatchingSessionType.VIEW;
        if (check(destination,watchingChatUrlPattern())) type = WatchingSessionType.CHAT;

        return switch (type) {
            case VIEW -> merge(MESSAGE_VIEW_PREFIX, MatchRule.extract(destination,UUID_REGEX));
            case CHAT -> merge(MESSAGE_CHAT_PREFIX, MatchRule.extract(destination,UUID_REGEX));
            default -> throw new DestinationInvalidException(null);
        };
    }

    private String merge(String p , List<String> q){
        if (q.isEmpty()) throw new DestinationInvalidException(null);
        return p + q.get(0);
    }


    @Override
    public void joinViewerSession(SessionInfo info) {
        String session = Objects.requireNonNull(info.session());
        String subscribe = Objects.requireNonNull(info.subscription());
        checkAuth(info);

        List<String> items = WATCHING_VIEW_PATTERN.extract(info.destination());
        if (items.isEmpty()) throw new DestinationInvalidException(null);

        String content = items.get(0);

        String topic = topic(info);

        watchingSessionRepository.register(session,subscribe,content);
        topicRegistry.register(topic, session, subscribe, viewSessionMessageListener);
    }


    @Override
    public void leaveViewerSession(SessionInfo info) {
        String session = Objects.requireNonNull(info.session());
        String subscribe = Objects.requireNonNull(info.subscription());
        checkAuth(info);

        watchingSessionRepository.unregister(session,subscribe);
        topicRegistry.unregister(session, subscribe);
    }

    @Override
    public void joinChatSession(SessionInfo info) {

    }

    @Override
    public void leaveChatSession(SessionInfo info) {

    }

    private void checkAuth(SessionInfo info){
        String destination = Objects.requireNonNull(info.destination());
        SecurityUser user = Objects.requireNonNull(info.user());

        if (!user.isEnabled())
            throw new UserNotFoundException(null); // todo - 권한 에러로 변경?

        // chat 이나 view 경로가 아니면 에러 반환.
        if(!(MatchRule.check(destination,watchingSessionUrlPattern())
                || MatchRule.check(destination,watchingChatUrlPattern())))
            throw new DestinationInvalidException(null);
    }


}
