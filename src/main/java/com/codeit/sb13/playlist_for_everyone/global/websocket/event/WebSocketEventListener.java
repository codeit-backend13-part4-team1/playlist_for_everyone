package com.codeit.sb13.playlist_for_everyone.global.websocket.event;


import com.codeit.sb13.playlist_for_everyone.global.exception.user.UserNotFoundException;
import com.codeit.sb13.playlist_for_everyone.global.exception.websocket.WebSocketSessionNotFoundException;
import com.codeit.sb13.playlist_for_everyone.global.websocket.SessionInfo;
import com.codeit.sb13.playlist_for_everyone.global.websocket.matcher.DestinationMatcher;
import com.codeit.sb13.playlist_for_everyone.global.websocket.matcher.SessionMatcher;
import com.codeit.sb13.playlist_for_everyone.global.websocket.registry.session.WebSocketRegistry;
import com.codeit.sb13.playlist_for_everyone.global.websocket.registry.subscription.SubscriptionRegistry;
import com.codeit.sb13.playlist_for_everyone.global.websocket.watching.role.*;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.user.SimpUser;
import org.springframework.messaging.simp.user.SimpUserRegistry;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.*;

import java.util.Objects;

import static com.codeit.sb13.playlist_for_everyone.global.websocket.watching.config.WatchingSessionProperties.*;

/**
 * 웹 소켓의 연결 및 구독상태의 전환을 알리는 이벤트.
 * 리소스가 Spring에 정상적으로 반영되었는지는 알 수 없음.
 */

@Slf4j
@Component
@RequiredArgsConstructor
public class WebSocketEventListener {

    private final SubscriptionRegistry subscriptionRegistry;
    private final SimpUserRegistry userRegistry;

    private final DestinationMatcher subscribe;
    private final DestinationMatcher unsubscribe;
    private final SessionMatcher disconnect;
    private final WebSocketRegistry webSocketRegistry;

    @PostConstruct
    public void init(){
        // watchingSession 경로 이벤트 등록.
        subscribe.add(new WatchingSessionJoinRule(watchingSessionUrlPattern()));
        subscribe.add(new ChattingSessionJoinRule(watchingChatUrlPattern()));
        unsubscribe.add(new WatchingSessionLeaveRule(watchingSessionUrlPattern()));
        unsubscribe.add(new ChattingSessionLeaveRule(watchingChatUrlPattern()));
        disconnect.add(new WatchingServiceDisconnectRule());
    }

    /**
     * Connection 프레임을 수신 했을 때, ChannelInterceptor 가 preSend() 로 executor 에 작업을 등록 한 이후,
     * 동기적으로 발생 시키는 이벤트.
     * @param event 일반적인 메세지를 담고있음.
     */
    @EventListener
    public void onConnect(SessionConnectEvent event){
    }

    /**
     * Stomp 브로커가 Connect 프레임에 의해 Connected 프레임 응답을 생성하고 난 후,
     * 동기적으로 발생 시키는 이벤트.
     * @param event 일반적인 메세지를 담고있음.
     */
    @EventListener
    public void onConnected(SessionConnectedEvent event){
    }

    /**
     * ChannelInterceptor 가 preSend() 로 executor 에 작업을 등록 한 이후,
     * 동기적으로 발생 시키는 이벤트.
     * @param event 일반적인 메세지(세션, 구독, "Destination")와, Principal 을 담고있음.
     */
    @EventListener
    public void onSubscribe(SessionSubscribeEvent event){
        SessionInfo info = new SessionInfo(event.getMessage(), event.getUser());
        subscribe.run(info);
//        subscriptionRegistry.register(info); // todo - 비즈니스 로직 마지막에 등록하는 식으로 변동
    }

    /**
     * Unsubscription 프레임을 수신한 직후 발생되는 이벤트.
     * ChannelInterceptor 가 preSend() 로 executor 에 작업을 등록 한 이후,
     * 동기적으로 발생 시키는 이벤트.
     * @param event 일반적인 메세지(세션, 구독)와, Principal 을 담고있음.
     */
    @EventListener
    public void onUnsubscribe(SessionUnsubscribeEvent event){
        // 미리 등록된 저장소에서 경로 정보 복원.
        SessionInfo info = subscriptionRegistry.get(new SessionInfo(event.getMessage(), event.getUser()))
                .orElseThrow(WebSocketSessionNotFoundException::new);

        unsubscribe.run(info);
//        subscriptionRegistry.unregister(info); // todo - 비즈니스 로직 마지막에 해제하는 식으로 변동.
    }

    /**
     * 웹 소켓 연결이 종료되고 난 후,
     * Disconnect 메세지를 ChannelInterceptor 에 "송신하기 전"에
     * 동기적으로 발생시키는 이벤트.
     * @param event 세션Id 와 closeStatus 를 담고 있음.
     */
    @EventListener
    public void onDisconnect(SessionDisconnectEvent event){
        SessionInfo info = new SessionInfo(event.getMessage(), event.getUser());
        disconnect.run(info);
    }


    /**
     * 로그인 된 유저의 인증 정보가 취소되었을 때 발생하는 이벤트.
     * 인증된 유저에 대한 모든 세션 연결을 종료한다.
     * @param event 취소될 유저 인증정보가 담긴 매서드
     */
    @EventListener
    public void onAuthRevoke(WebSocketAuthRevokeEvent event){
        String authUserName = Objects.requireNonNull(event.user())
                .getUsername();
        SimpUser user = userRegistry.getUser(authUserName);

        if (user != null)
            user.getSessions().forEach(session -> {
                String sessionId = session.getId();
                try {
                    webSocketRegistry.close(sessionId);
                } catch (Exception e) {
                    log.error("[WebSocketAuthRevokeEvent] 세션 종료에 실패 했습니다. 세션ID : {}", sessionId);
                }
            });
    }
}
