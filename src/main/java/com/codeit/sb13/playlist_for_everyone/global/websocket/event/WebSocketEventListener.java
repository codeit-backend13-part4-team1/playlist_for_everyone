package com.codeit.sb13.playlist_for_everyone.global.websocket.event;


import com.codeit.sb13.playlist_for_everyone.global.exception.websocket.WebSocketSessionNotFoundException;
import com.codeit.sb13.playlist_for_everyone.global.websocket.SessionInfo;
import com.codeit.sb13.playlist_for_everyone.global.websocket.matcher.DestinationMatcher;
import com.codeit.sb13.playlist_for_everyone.global.websocket.matcher.SessionMatcher;
import com.codeit.sb13.playlist_for_everyone.global.websocket.registry.subscription.SubscriptionRegistry;
import com.codeit.sb13.playlist_for_everyone.global.websocket.watching.role.*;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.*;

import static com.codeit.sb13.playlist_for_everyone.global.websocket.watching.config.WatchingSessionProperties.*;

/**
 * 웹 소켓의 연결 및 구독상태의 전환을 알리는 이벤트.
 * 리소스가 Spring에 정상적으로 반영되었는지는 알 수 없음.
 */
@Component
@RequiredArgsConstructor
public class WebSocketEventListener {

    private final SubscriptionRegistry subscriptionRegistry;

    private final DestinationMatcher subscribe;
    private final DestinationMatcher unsubscribe;
    private final SessionMatcher disconnect;

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


}
