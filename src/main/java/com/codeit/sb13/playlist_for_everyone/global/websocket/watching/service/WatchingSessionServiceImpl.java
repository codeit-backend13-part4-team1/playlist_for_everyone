package com.codeit.sb13.playlist_for_everyone.global.websocket.watching.service;

import com.codeit.sb13.playlist_for_everyone.domain.auth.security.SecurityUser;
import com.codeit.sb13.playlist_for_everyone.domain.content.service.ContentService;
import com.codeit.sb13.playlist_for_everyone.domain.user.entity.User;
import com.codeit.sb13.playlist_for_everyone.domain.user.service.UserService;
import com.codeit.sb13.playlist_for_everyone.global.service.redis.RedisMessageDto;
import com.codeit.sb13.playlist_for_everyone.global.service.redis.RedisTopicRegistry;
import com.codeit.sb13.playlist_for_everyone.global.websocket.registry.ConnectionRegistry;
import com.codeit.sb13.playlist_for_everyone.global.websocket.SessionData;
import com.codeit.sb13.playlist_for_everyone.global.websocket.watching.WatchingSessionMessageListener;
import com.codeit.sb13.playlist_for_everyone.global.websocket.watching.repository.WatchingSessionRepository;
import com.codeit.sb13.playlist_for_everyone.global.websocket.watching.dto.watcher.*;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import static com.codeit.sb13.playlist_for_everyone.global.websocket.watching.config.WatchingSessionProperties.*;

@Component
@RequiredArgsConstructor
public class WatchingSessionServiceImpl implements WatchingSessionService {

    private final ConnectionRegistry connectionRegistry;
    private final WatchingSessionRepository watchingSessionRepository;
    private final RedisTopicRegistry redisTopicRegistry;
    private final UserService userService;
    private final ContentService contentService;

    private final RedisTemplate<String, Object> redisTemplate;

    private final WatchingSessionMessageListener watchingSessionMessageListener;

    private User getUserFromConnection(SessionData connection) {
        SecurityUser principal = connection.user();
        return userService.getById(principal.getUserId());
    }

    // 레디스 메세지 관리자에 등록할 시청 토픽
    private String redisMessageViewTopic(String contentId){
        return MESSAGE_VIEW_PREFIX + contentId;
    }

    private String redisMessageChatTopic(String contentId){
        return MESSAGE_CHAT_PREFIX + contentId;
    }

    @Override
    public void subscribeWatchingSession(String sessionId, String subscribe, String content) {

        SessionData connection = connectionRegistry.get(sessionId, subscribe);
        User user = getUserFromConnection(connection);

        // 연결 정보를 기반으로 redis 시청 채널 등록
        watchingSessionRepository.register(
                connection.session(),
                connection.subscription(),
                content
        );

        // 이후, 메세지 전송 채널 구독
        redisTopicRegistry.register(
                redisMessageViewTopic(content),
                connection.session(),
                connection.subscription(),
                watchingSessionMessageListener
        );

        WatchContent watchContent = new WatchContent(
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null
        );

        Watcher watcher = new Watcher(
                user.getId(),
                user.getName(),
                null    // profile image url
        );

        WatchingSessionDto watchingSessionDto = new WatchingSessionDto(
                connection.id(),
                connection.createdAt(),
                watcher,
                watchContent
        );

        WatchingSessionChange watchingSessionChange = new WatchingSessionChange(
                ChangeType.JOIN,
                watchingSessionDto,
                watchingSessionRepository.getWatcherCount(content)
        );

        RedisMessageDto<WatchingSessionChange> redisMessageDto = new RedisMessageDto<>(
                content,
                watchingSessionChange
        );


        // 메세지 발신.
        redisTemplate.convertAndSend(
                redisMessageViewTopic(content),
                redisMessageDto);


    }

    @Override
    public void unsubscribeWatchingSession(String sessionId, @Nullable String subscribe) {

        SessionData connection = connectionRegistry.get(sessionId, subscribe);
        User user = getUserFromConnection(connection);

        String content = watchingSessionRepository.getContent(connection.session(),connection.subscription());


        watchingSessionRepository.unregister(
                connection.session(),
                connection.subscription()
        );

        // 이후, 메세지 전송 채널 구독
        redisTopicRegistry.unregister(
                connection.session(),
                connection.subscription()
        );

        WatchContent watchContent = new WatchContent(
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null
        );

        Watcher watcher = new Watcher(
                user.getId(),
                user.getName(),
                null    // profile image url
        );

        WatchingSessionDto watchingSessionDto = new WatchingSessionDto(
                connection.id(),
                connection.createdAt(),
                watcher,
                watchContent
        );


        // 구독 해제 이후 데이터를 전달.
        WatchingSessionChange watchingSessionChange = new WatchingSessionChange(
                ChangeType.LEAVE,
                watchingSessionDto,
                watchingSessionRepository.getWatcherCount(content)
        );

        RedisMessageDto<WatchingSessionChange> redisMessageDto = new RedisMessageDto<>(
                content,
                watchingSessionChange
        );


        // 메세지 발신.
        redisTemplate.convertAndSend(
                redisMessageViewTopic(content),
                redisMessageDto);


    }

    @Override
    public void subscribeChatSession(String sessionId, String subscribe, String content) {

        SessionData connection = connectionRegistry.get(sessionId, subscribe);
        User user = getUserFromConnection(connection);

        // redis 메세지 컨테이너 구독
        redisTopicRegistry.register(
                redisMessageChatTopic(content),
                connection.session(),
                connection.subscription(),
                watchingSessionMessageListener
        );
    }

    @Override
    public void unsubscribeChatSession(String sessionId, @Nullable String subscribe) {

        SessionData connection = connectionRegistry.get(sessionId, subscribe);
        User user = getUserFromConnection(connection);

        // redis 메세지 컨테이너 구독
        redisTopicRegistry.unregister(
                connection.session(),
                connection.subscription()
        );
    }

}
