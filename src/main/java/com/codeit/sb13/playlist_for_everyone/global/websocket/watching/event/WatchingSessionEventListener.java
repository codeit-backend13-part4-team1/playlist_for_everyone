package com.codeit.sb13.playlist_for_everyone.global.websocket.watching.event;

import com.codeit.sb13.playlist_for_everyone.domain.auth.security.SecurityUser;
import com.codeit.sb13.playlist_for_everyone.domain.user.service.UserService;
import com.codeit.sb13.playlist_for_everyone.global.exception.websocket.WebSocketDestinationInvalidException;
import com.codeit.sb13.playlist_for_everyone.global.exception.websocket.WebSocketSessionNotFoundException;
import com.codeit.sb13.playlist_for_everyone.global.exception.websocket.WebSocketSessionRegisterException;
import com.codeit.sb13.playlist_for_everyone.global.service.MatchRule;
import com.codeit.sb13.playlist_for_everyone.global.service.redis.RedisMessageDto;
import com.codeit.sb13.playlist_for_everyone.global.websocket.SessionInfo;
import com.codeit.sb13.playlist_for_everyone.global.websocket.watching.WatchingSession;
import com.codeit.sb13.playlist_for_everyone.global.websocket.watching.dto.chat.ContentChatDto;
import com.codeit.sb13.playlist_for_everyone.global.websocket.watching.dto.watcher.WatchingSessionChange;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.util.Objects;

import static com.codeit.sb13.playlist_for_everyone.global.websocket.watching.config.WatchingSessionProperties.*;

@Slf4j
@Component
@RequiredArgsConstructor
public class WatchingSessionEventListener {


    private final UserService userService;
    private final WatchingSession watchingSession;
    private final RedisTemplate<String, Object> redisTemplate;

    /**
     * 세션 등록 이벤트는 동기 이벤트로 진행한다.
     * 세션 등록에 실패하면, 구독 프레임이 실패했다는 에러를 발생 시키기 위함.
     * @param event info -> SessionInfo
     */
    @EventListener
    public void watchJoinEvent(WatchingSessionJoinEvent event){
        SessionInfo info = event.info();
        try {
            Objects.requireNonNull(info.destination());

            String topic = Objects.requireNonNull(watchingSession.topic(info));

            WatchingSessionChange data = Objects
                    .requireNonNull(watchingSession.joinViewerSession(event.info()));

            RedisMessageDto<WatchingSessionChange> message = new RedisMessageDto<>(info.destination(), data);

            redisTemplate.convertAndSend(topic, message);
        } catch (Exception e){
            // do nothing
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
            Objects.requireNonNull(info.destination());

            String topic = Objects.requireNonNull(watchingSession.topic(info));

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
        }
    }

    @EventListener
    public void watchRefreshEvent(WatchingSessionRefreshEvent event){
        watchingSession.refresh(event.info());
    }


    @EventListener
    public void chatJoinEvent(WatchingChatJoinEvent event){
        SessionInfo info = Objects.requireNonNull(event.info());
        watchingSession.joinChatSession(info);
    }

    @EventListener
    public void chatLeaveEvent(WatchingChatLeaveEvent event){
        SessionInfo info = Objects.requireNonNull(event.info());
        watchingSession.leaveChatSession(info);
    }

    @EventListener
    public void chatSendEvent(WatchingChatSendEvent event){
        try {
            SessionInfo info = Objects.requireNonNull(event.info());
            String payload = event.message();
            String topic = watchingSession.topic(info);

            boolean destinationCheck = MatchRule.check(
                    info.destination(),
                    "/pub/contents/",
                    UUID_REGEX,
                    CHAT_POSTFIX
            );

            if (!destinationCheck) throw new WebSocketDestinationInvalidException();

            ContentChatDto chat = watchingSession.getContentChatDto(info, payload);

            RedisMessageDto<ContentChatDto> message = new RedisMessageDto<>(
                    info.destination(),
                    chat
            );

            redisTemplate.convertAndSend(topic, message);
        } catch (RuntimeException e) {
            log.error("[{}] 메세지 전송 실패.",
                    getClass().getSimpleName()
            );
            // todo - message send exception 작성.
            throw new WebSocketSessionNotFoundException();
        }

    }

}
