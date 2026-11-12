package com.codeit.sb13.playlist_for_everyone.global.websocket.watching.event;

import com.codeit.sb13.playlist_for_everyone.domain.auth.security.SecurityUser;
import com.codeit.sb13.playlist_for_everyone.global.exception.websocket.WebSocketDestinationInvalidException;
import com.codeit.sb13.playlist_for_everyone.global.exception.websocket.WebSocketSessionNotFoundException;
import com.codeit.sb13.playlist_for_everyone.global.exception.websocket.WebSocketSessionRegisterException;
import com.codeit.sb13.playlist_for_everyone.global.service.MatchRule;
import com.codeit.sb13.playlist_for_everyone.global.service.redis.RedisMessageDto;
import com.codeit.sb13.playlist_for_everyone.global.websocket.SessionInfo;
import com.codeit.sb13.playlist_for_everyone.global.websocket.registry.subscription.SubscriptionRegistry;
import com.codeit.sb13.playlist_for_everyone.global.websocket.watching.WatchingSession;
import com.codeit.sb13.playlist_for_everyone.global.websocket.watching.dto.chat.ContentChatDto;
import com.codeit.sb13.playlist_for_everyone.global.websocket.watching.dto.watcher.WatchingSessionChange;
import com.codeit.sb13.playlist_for_everyone.global.websocket.watching.event.chat.WatchingChatJoinEvent;
import com.codeit.sb13.playlist_for_everyone.global.websocket.watching.event.chat.WatchingChatLeaveEvent;
import com.codeit.sb13.playlist_for_everyone.global.websocket.watching.event.chat.WatchingChatSendEvent;
import com.codeit.sb13.playlist_for_everyone.global.websocket.watching.event.view.WatchingSessionJoinEvent;
import com.codeit.sb13.playlist_for_everyone.global.websocket.watching.event.view.WatchingSessionLeaveEvent;
import com.codeit.sb13.playlist_for_everyone.global.websocket.watching.event.view.WatchingSessionRefreshEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.util.Objects;
import java.util.UUID;

import static com.codeit.sb13.playlist_for_everyone.global.websocket.watching.config.WatchingSessionProperties.*;
import static com.codeit.sb13.playlist_for_everyone.global.websocket.watching.WebSocketUtils.check;

@Slf4j
@Component
@RequiredArgsConstructor
public class WatchingSessionEventListener {
    private final WatchingSession watchingSession;
    private final RedisTemplate<String, Object> redisTemplate;
    private final SubscriptionRegistry subscriptionRegistry;

    /**
     * 세션 등록 이벤트는 동기 이벤트로 진행한다.
     * 세션 등록에 실패하면, 구독 프레임이 실패했다는 에러를 발생 시키기 위함.
     * @param event info -> SessionInfo
     */
    @EventListener
    public void watchJoinEvent(WatchingSessionJoinEvent event){
        SessionInfo info = event.info();

        // 내부 로직에서 등록된 구독을 참고해야 할 수 있으므로 먼저 등록.
        subscriptionRegistry.register(info);
        try {
            check(info.destination());

            String topic = check(watchingSession.topic(info));

            WatchingSessionChange data = check(watchingSession.joinViewerSession(event.info()));

            RedisMessageDto<WatchingSessionChange> message = new RedisMessageDto<>(info.destination(), data);

            redisTemplate.convertAndSend(topic, message);
        } catch (Exception e){
            // 등록에 실패하면 구독 삭제.
            subscriptionRegistry.unregister(info);

            SecurityUser user = info.user();
            log.error("[{}] sessionId : {}(user : {}) - 동시 시청 참여 로직 에러.",
                    this.getClass().getSimpleName(),
                    info.session(),
                    user != null ? user.getUserId() : null,
                    e);
            throw new WebSocketSessionRegisterException();
        }
    }

    /**
     * 동시 시청 세션을 종료한 이벤트.
     * 동시시청 레지스트리에서 등록을 취소하고, 현재 동시 시청자 수를 기준으로 메세지를 발행한다.
     * @param event info -> SessionInfo
     */
    @EventListener
    public void watchLeaveEvent(WatchingSessionLeaveEvent event){
        SessionInfo info = event.info();
        try {
            check(info.destination());

            String topic = check(watchingSession.topic(info));

            WatchingSessionChange data = Objects
                    .requireNonNull(watchingSession.leaveViewerSession(event.info()));

            RedisMessageDto<WatchingSessionChange> message = new RedisMessageDto<>(info.destination(), data);

            redisTemplate.convertAndSend(topic, message);
        } catch (Exception e){
            // do nothing
            SecurityUser user = info.user();
            log.error("[{}] sessionId : {}(user : {}) - 동시 시청 퇴장 로직 에러.",
                    this.getClass().getSimpleName(),
                    info.session(),
                    user != null ? user.getUserId() : null,
                    e);
        } finally {
            // 비즈니스 로직이 실패하더라도 구독은 해제되어야 한다.
            subscriptionRegistry.unregister(info);
        }
    }

    /**
     * 하트비트 로직에 의한 등록된 세션 리프레시.
     * @param event 세션 info 를 담고있다.
     */
    @EventListener
    public void watchRefreshEvent(WatchingSessionRefreshEvent event){
        watchingSession.refresh(event.info());
    }


    /**
     * 동시 시청 세션 채팅방 입장 이벤트
     * @param event 세션 정보를 들고 있다.
     */
    @EventListener
    public void chatJoinEvent(WatchingChatJoinEvent event){
        SessionInfo info = check(event.info());
        subscriptionRegistry.register(info);
        watchingSession.joinChatSession(info);
    }

    /**
     * 동시 시청 세션 채팅방 퇴장 이벤트.
     * @param event 세션 정보를 들고 있다.
     */
    @EventListener
    public void chatLeaveEvent(WatchingChatLeaveEvent event){
        SessionInfo info = check(event.info());
        watchingSession.leaveChatSession(info);
        subscriptionRegistry.unregister(info);
    }

    /**
     * 채팅 전송 이벤트.
     * 세션 정보와 함께 채팅 메세지 내용을 들고있다.
     * @param event 세션 정보(info), 메세지(message)
     */
    @EventListener
    public void chatSendEvent(WatchingChatSendEvent event){
        try {
            SessionInfo info = check(event.info());
            String payload = event.message();
            String topic = watchingSession.topic(info);
            MatchRule contentRule =
                    MatchRule.builder()
                            .origin(info.destination())
                            .prefix("/pub/contents/")
                            .pattern(UUID_REGEX)
                            .postfix(CHAT_POSTFIX)
                            .build();

            if (!contentRule.check()) throw new WebSocketDestinationInvalidException();
            String destination = PATH_PREFIX + contentRule.extract().get(0) + CHAT_POSTFIX;

            ContentChatDto chat = watchingSession.getContentChatDto(info, payload);

            RedisMessageDto<ContentChatDto> message = new RedisMessageDto<>(
                    destination,
                    chat
            );
            log.info("contentChatSend : {}", message);
            redisTemplate.convertAndSend(topic, message);
        } catch (RuntimeException e) {
            log.error("[{}] 메세지 전송 실패.",
                    getClass().getSimpleName(),
                    e
            );
            // todo - message send exception 작성. 핸들링 때문에 에러를 그대로 반환 안 하고, 여기에서 에러처리 역시 고심.
            throw new WebSocketSessionNotFoundException();
        }

    }

}
