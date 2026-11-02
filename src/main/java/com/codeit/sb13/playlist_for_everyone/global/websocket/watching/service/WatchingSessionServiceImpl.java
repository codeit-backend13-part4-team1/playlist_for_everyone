package com.codeit.sb13.playlist_for_everyone.global.websocket.watching.service;

import com.codeit.sb13.playlist_for_everyone.domain.auth.security.SecurityUser;
import com.codeit.sb13.playlist_for_everyone.domain.content.service.ContentService;
import com.codeit.sb13.playlist_for_everyone.domain.user.entity.User;
import com.codeit.sb13.playlist_for_everyone.domain.user.service.UserService;
import com.codeit.sb13.playlist_for_everyone.global.service.RedisMessageDto;
import com.codeit.sb13.playlist_for_everyone.global.service.RedisTopicRegistry;
import com.codeit.sb13.playlist_for_everyone.global.websocket.registry.ConnectionRegistry;
import com.codeit.sb13.playlist_for_everyone.global.websocket.SocketConnection;
import com.codeit.sb13.playlist_for_everyone.global.websocket.service.WatcherRegistry;
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
    private final WatcherRegistry watcherRegistry;
    private final RedisTopicRegistry redisTopicRegistry;
    private final UserService userService;
    private final ContentService contentService;

    private final RedisTemplate<String, Object> redisTemplate;

    private User getUserFromConnection(SocketConnection connection) {
        SecurityUser principal = (SecurityUser) connection.principal();
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

        SocketConnection connection = connectionRegistry.get(sessionId, subscribe);
        User user = getUserFromConnection(connection);

        // 연결 정보를 기반으로 redis 시청 채널 등록
        watcherRegistry.register(
                connection.session(),
                connection.subscription(),
                content
        );

        // 이후, 메세지 전송 채널 구독
        redisTopicRegistry.register(
                redisMessageViewTopic(content),
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

        WatchingSessionChange watchingSessionChange = new WatchingSessionChange(
                ChangeType.JOIN,
                watchingSessionDto,
                watcherRegistry.getWatcherCount(content)
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

        SocketConnection connection = connectionRegistry.get(sessionId, subscribe);
        User user = getUserFromConnection(connection);

        String content = watcherRegistry.getContent(connection.session(),connection.subscription());


        watcherRegistry.unregister(
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
                watcherRegistry.getWatcherCount(content)
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

        SocketConnection connection = connectionRegistry.get(sessionId, subscribe);
        User user = getUserFromConnection(connection);

        // redis 메세지 컨테이너 구독
        redisTopicRegistry.register(
                redisMessageChatTopic(content),
                connection.session(),
                connection.subscription()
        );
    }

    @Override
    public void unsubscribeChatSession(String sessionId, @Nullable String subscribe) {

        SocketConnection connection = connectionRegistry.get(sessionId, subscribe);
        User user = getUserFromConnection(connection);

        // redis 메세지 컨테이너 구독
        redisTopicRegistry.unregister(
                connection.session(),
                connection.subscription()
        );
    }

//    @Override
//    public void sendChat(String sessionId, String subscribe, String content) {
//
//    }
}
