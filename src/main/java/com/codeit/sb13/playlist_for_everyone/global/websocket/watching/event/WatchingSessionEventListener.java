package com.codeit.sb13.playlist_for_everyone.global.websocket.watching.event;


import com.codeit.sb13.playlist_for_everyone.domain.auth.security.SecurityUser;
import com.codeit.sb13.playlist_for_everyone.global.exception.websocket.WebSocketSessionRegisterException;
import com.codeit.sb13.playlist_for_everyone.global.service.MatchRule;
import com.codeit.sb13.playlist_for_everyone.global.websocket.SessionInfo;
import com.codeit.sb13.playlist_for_everyone.global.websocket.watching.event.chat.WatchingChatJoinEvent;
import com.codeit.sb13.playlist_for_everyone.global.websocket.watching.event.chat.WatchingChatLeaveEvent;
import com.codeit.sb13.playlist_for_everyone.global.websocket.watching.event.chat.WatchingChatSendEvent;
import com.codeit.sb13.playlist_for_everyone.global.websocket.watching.event.view.WatchingSessionJoinEvent;
import com.codeit.sb13.playlist_for_everyone.global.websocket.watching.event.view.WatchingSessionLeaveEvent;
import com.codeit.sb13.playlist_for_everyone.global.websocket.watching.event.view.WatchingSessionRefreshEvent;
import com.codeit.sb13.playlist_for_everyone.global.websocket.watching.service.WatchingSessionService;
import com.codeit.sb13.playlist_for_everyone.global.websocket.watching.service.chatting.ChattingSessionService;
import com.codeit.sb13.playlist_for_everyone.global.websocket.watching.service.viewing.ViewingSessionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.Objects;
import java.util.UUID;

import static com.codeit.sb13.playlist_for_everyone.global.websocket.watching.WebSocketUtils.check;
import static com.codeit.sb13.playlist_for_everyone.global.websocket.watching.config.WatchingSessionProperties.UUID_REGEX;

/**
 * 비즈니스 로직을 호출 하기 위한 이벤트 리스너
 * 실질적인 세션 연결 밑 끊기 관리는 하지 않고, 구독 관리만 한다.
 */

@Slf4j
@Component
@RequiredArgsConstructor
public class WatchingSessionEventListener {
    private final WatchingSessionService watchingSessionService;
    private final ChattingSessionService chatService;
    private final ViewingSessionService viewService;

    /**
     * 구독 등록 이벤트.
     * @param event info -> SessionInfo
     */
    @EventListener
    public void watchJoinEvent(WatchingSessionJoinEvent event){
        try {
            SessionInfo info = Objects.requireNonNull(event.info());

            watchingSessionService.joinViewerSession(
                    info.session(),
                    info.subscription(),
                    info.destination(),
                    info.user()
            );
        } catch (RuntimeException e) {
            log.error("[{}] 비즈니스 로직 처리 중 에러 발생",
                    this.getClass().getSimpleName(),
                    e);
            throw new WebSocketSessionRegisterException(e);
        }
    }

    /**
     * 동시 시청 세션을 종료한 이벤트.
     * 동시시청 레지스트리에서 등록을 취소하고, 현재 동시 시청자 수를 기준으로 메세지를 발행한다.
     * @param event info -> SessionInfo
     */
    @EventListener
    public void watchLeaveEvent(WatchingSessionLeaveEvent event){
        try {
            SessionInfo info = Objects.requireNonNull(event.info());

            watchingSessionService.leaveViewerSession(
                    info.session(),
                    info.subscription(),
                    info.user()
            );
        } catch (RuntimeException e) {
            log.error("[{}] 비즈니스 로직 처리 중 에러 발생",
                    this.getClass().getSimpleName(),
                    e);
        }
    }

    /**
     * 하트비트 로직에 의한 등록된 세션 리프레시.
     * @param event 세션 info 를 담고있다.
     */
    @EventListener
    public void watchRefreshEvent(WatchingSessionRefreshEvent event){
        try {
            SessionInfo info = Objects.requireNonNull(event.info());
            watchingSessionService.refresh(Objects.requireNonNull(info.session()));
        } catch (RuntimeException e) {
            log.error("[{}] 비즈니스 로직 처리 중 에러 발생",
                    this.getClass().getSimpleName(),
                    e);
        }
    }


    /**
     * 동시 시청 세션 채팅방 입장 이벤트
     * @param event 세션 정보를 들고 있다.
     */
    @EventListener
    public void chatJoinEvent(WatchingChatJoinEvent event){
        try {
            SessionInfo info = Objects.requireNonNull(event.info());
            watchingSessionService.joinChattingSession(
                    info.session(),
                    info.subscription(),
                    info.destination(),
                    info.user()
            );
        } catch (RuntimeException e) {
            log.error("[{}] 비즈니스 로직 처리 중 에러 발생",
                    this.getClass().getSimpleName(),
                    e);
            throw new WebSocketSessionRegisterException(e);
        }

    }

    /**
     * 동시 시청 세션 채팅방 퇴장 이벤트.
     * @param event 세션 정보를 들고 있다.
     */
    @EventListener
    public void chatLeaveEvent(WatchingChatLeaveEvent event){
        try {
            SessionInfo info = Objects.requireNonNull(event.info());
            watchingSessionService.leaveChattingSession(
                    info.session(),
                    info.subscription(),
                    info.user()
            );
        } catch (RuntimeException e) {
            log.error("[{}] 비즈니스 로직 처리 중 에러 발생",
                    this.getClass().getSimpleName(),
                    e);
        }
    }

    /**
     * 채팅 전송 이벤트.
     * 세션 정보와 함께 채팅 메세지 내용을 들고있다.
     * @param event 세션 정보(info), 메세지(message)
     */
    @EventListener
    public void chatSendEvent(WatchingChatSendEvent event) {
        try {
            SessionInfo info = Objects.requireNonNull(event.info());
            String destination = Objects.requireNonNull(info.destination());
            SecurityUser user = Objects.requireNonNull(info.user());
            MatchRule UUID_PATTERN = MatchRule
                    .builder()
                    .prefix("/pus/contents")
                    .pattern(UUID_REGEX)
                    .postfix("/chat")
                    .build();
            UUID contentId = UUID.fromString(UUID_PATTERN.extract(destination).get(0));

            watchingSessionService.sendChatMessage(contentId, user.getUserId(), event.message());
        } catch (RuntimeException e) {
            log.error("[{}] 비즈니스 로직 처리 중 에러 발생",
                    this.getClass().getSimpleName(),
                    e);
        }
    }
    /**
     * 세션 연결이 종료되었을 때, 잔존 리소스를 정리하기 위한 이벤트.
     * @param event 세션 정보(sessionId)
     */
    @EventListener
    public void DisconnectEvent(WatchingSessionDisconnectEvent event) {
        try {
            SessionInfo info = check(event.info());
            watchingSessionService.cleanUpWithSessionId(info.session());
        } catch (RuntimeException e) {
            log.error("[{}] 비즈니스 로직 처리 중 에러 발생",
                    this.getClass().getSimpleName(),
                    e);
        }
    }

}
