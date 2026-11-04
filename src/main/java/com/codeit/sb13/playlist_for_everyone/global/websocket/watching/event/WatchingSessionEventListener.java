package com.codeit.sb13.playlist_for_everyone.global.websocket.watching.event;

import com.codeit.sb13.playlist_for_everyone.global.websocket.watching.repository.WatcherRegistry;
import com.codeit.sb13.playlist_for_everyone.global.websocket.watching.service.WatchingSessionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class WatchingSessionEventListener {

    private final WatchingSessionService watchingSessionService;
    private final WatcherRegistry watcherRegistry;

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
        watchingSessionService.subscribeWatchingSession(event.sessionId(), event.subscriptionId(), event.destination());

    }

    /**
     * 동시 시청 세션을 종료한 이벤트.
     * 동시시청 레지스트리에서 등록을 취소하고, 현재 동시 시청자 수를 기준으로 메세지를 발행한다.
     * @param event
     */
    @EventListener
    public void watchLeaveEvent(WatchingSessionLeaveEvent event){
        watchingSessionService.unsubscribeWatchingSession(event.sessionId(), event.subscriptionId());
    }

    @EventListener
    public void watchRefreshEvent(WatchingSessionRefreshEvent event){
        watcherRegistry.refresh(event.session());
    }


    @EventListener
    public void chatJoinEvent(WatchingChatJoinEvent event){
        watchingSessionService.subscribeChatSession(event.sessionId(), event.subscriptionId(),event.contentId());
    }

    @EventListener
    public void chatLeaveEvent(WatchingChatLeaveEvent event){
        watchingSessionService.unsubscribeChatSession(event.sessionId(), event.subscriptionId());
    }


    private String pathResolve(String prefix, UUID contentId, String postfix){
        return prefix + contentId + postfix ;
    }

}
