package com.codeit.sb13.playlist_for_everyone.global.websocket.watching.event;

import com.codeit.sb13.playlist_for_everyone.domain.auth.security.SecurityUser;
import com.codeit.sb13.playlist_for_everyone.global.service.RedisTopicRegistry;
import com.codeit.sb13.playlist_for_everyone.global.websocket.watching.dto.watcher.WatchContent;
import com.codeit.sb13.playlist_for_everyone.global.websocket.watching.dto.watcher.Watcher;
import com.codeit.sb13.playlist_for_everyone.global.websocket.watching.dto.watcher.WatchingSessionDto;
import com.codeit.sb13.playlist_for_everyone.global.websocket.service.WatcherRegistry;
import com.codeit.sb13.playlist_for_everyone.global.websocket.watching.dto.chat.ContentChatDto;
import com.codeit.sb13.playlist_for_everyone.global.websocket.watching.dto.chat.RedisMessageChatDto;
import com.codeit.sb13.playlist_for_everyone.global.websocket.watching.dto.watcher.ChangeType;
import com.codeit.sb13.playlist_for_everyone.global.websocket.watching.dto.watcher.WatchingSessionChange;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class WatchingSessionEventListener {
    private final WatcherRegistry watcherRegistry;
    private final RedisTopicRegistry redisTopicRegistry;
    private final ChatMessagePublisher chatMessagePublisher;
    private final SimpMessagingTemplate messagingTemplate;

    static private final String SUB_PREFIX = "/sub/contents/";
    static private final String CHAT_POSTFIX = "/chat";
    static private final String WATCH_POSTFIX = "/watch";

    /**
     * 세션 등록 이벤트는 동기 이벤트로 진행한다.
     * 세션 등록에 실패하면, 구독 프레임이 실패했다는 에러를 발생 시키기 위함.
     * @param event 세션 등록에 필요한 인증, 컨텐츠 경로, 세션 과 구독 정보.
     */
    @EventListener
    public void watchJoinEvent(WatchingSessionJoinEvent event){

        // todo - 알림 이벤트 발행과 동일해야 함.

        watcherRegistry.register(event.contentId(), event.sessionId(), event.subscriptionId());

        // topic 이 메세지의 경로를 확인하지 않음.
        // 즉 content 자체 정보도 payload 로 전달되도록 해야 함.
        // 메세지 발행 목적지 역시 같이 보내줘야 함.
        redisTopicRegistry.register("topic", event.sessionId(), event.subscriptionId());

        // redis 메세지 전송을 통해 WatchSessionChange 를 전송하도록하도록 수정.

        // content Service 에서 조회 후 조립.
        new WatchContent(
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null
        );

        SecurityUser user = event.user();

        // user service 에서 조회 후 조립.
        new Watcher(
                null,
                null,
                null,
                null
        );

        // watchSessionService 에서 총 조합 후 반환.
        new WatchingSessionDto(
                UUID.randomUUID(),  // watchSessionEntity 에 대한 정보
                Instant.now(),  // watchSessionEntity 에 대한 정보
                null,
                null

        );


        String destination = SUB_PREFIX + event.contentId() + WATCH_POSTFIX;

        RedisMessageChatDto<WatchingSessionChange> message = new RedisMessageChatDto<>(
                "destination",
                new WatchingSessionChange(
                        ChangeType.JOIN,
                        null,
                        0L
                )
        );

        messagingTemplate.convertAndSend("topic", message);

        // 다 하나의 서비스로 통합 가능?

    }

    /**
     * 동시 시청 세션을 종료한 이벤트.
     * 동시시청 레지스트리에서 등록을 취소하고, 현재 동시 시청자 수를 기준으로 메세지를 발행한다.
     *
     * @param event
     */
    @EventListener
    public void watchLeaveEvent(WatchingSessionLeaveEvent event){
        watcherRegistry.unregister(event.sessionId(), event.subscriptionId());
        redisTopicRegistry.unregister(event.sessionId(), event.subscriptionId());

        RedisMessageChatDto<WatchingSessionChange> message = new RedisMessageChatDto<>(
                "destination",
                new WatchingSessionChange(
                        ChangeType.LEAVE,
                        null,
                        0L
                )
        );

        messagingTemplate.convertAndSend("topic", message);
    }


    @EventListener
    public void chatJoinEvent(WatchingChatJoinEvent event){
        redisTopicRegistry.register("topic",event.sessionId(), event.subscriptionId());
    }

    @EventListener
    public void chatLeaveEvent(WatchingChatLeaveEvent event){
        redisTopicRegistry.unregister(event.sessionId(), event.subscriptionId());
    }




    @EventListener
    public void chatSendEvent(WatchingChatSendEvent event){
//        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(event.getMessage());
//        String sessionId = accessor.getSessionId();
//        String subscriptionId = accessor.getSubscriptionId();
//
//        watcherRegistry.unregister(sessionId, subscriptionId);



        if (event.contentId() == null)
            throw new RuntimeException();

        String contentId = event.contentId().toString();
        ContentChatDto chatDto = new ContentChatDto(event.user(), event.message());

        chatMessagePublisher.publish(
                contentId,
                chatDto);
    }


    private String pathResolve(String prefix, UUID contentId, String postfix){
        return prefix + contentId + postfix ;
    }

}
