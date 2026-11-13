package com.codeit.sb13.playlist_for_everyone.global.websocket.event;

import com.codeit.sb13.playlist_for_everyone.global.websocket.event.auth.WebSocketAuthenticateRevokeEvent;
import com.codeit.sb13.playlist_for_everyone.global.websocket.service.WebSocketService;
import com.codeit.sb13.playlist_for_everyone.global.websocket.watching.WatchingSession;
import com.codeit.sb13.playlist_for_everyone.global.websocket.watching.event.WatchingSessionContentDeleteEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * 웹 소켓 도메인의 이벤트를 처리하는 클래스.
 * 단일 인스턴스 내에서의 처리를 담당한다.
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "websocket.event.multiple", havingValue = "false", matchIfMissing = true)
@RequiredArgsConstructor
public class LocalEventListener {
    private final WebSocketService webSocketService;
    private final WatchingSession watchingSession;

    /**
     * 인증이 만료된 유저의 세션을 삭제하는 이벤트
     * @param event 인증이 만료된 유저의 username 을 담고있다.
     */
    @EventListener
    public void onAuthRevoke(WebSocketAuthenticateRevokeEvent event){
        webSocketService.revokeAuth(event.username());
    }

    /**
     * 컨텐츠가 삭제되었을 때 발생되는 이벤트.
     * @param event 삭제된 컨텐츠 id 를 담고있다.
     */
    @EventListener
    public void onContentDelete(WatchingSessionContentDeleteEvent event){
        watchingSession.disconnectWithContentId(event.contentId());
    }

}
