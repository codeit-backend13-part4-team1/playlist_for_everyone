package com.codeit.sb13.playlist_for_everyone.global.websocket;


import com.codeit.sb13.playlist_for_everyone.global.websocket.matcher.DestinationMatcher;
import com.codeit.sb13.playlist_for_everyone.global.websocket.watching.WatchingSessionJoinRule;
import com.codeit.sb13.playlist_for_everyone.global.websocket.watching.WatchingSessionLeaveRule;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.*;

import java.util.regex.Pattern;

/**
 * 웹 소켓의 연결 및 구독상태의 전환을 알리는 이벤트.
 * 리소스가 Spring에 정상적으로 반영되었는지는 알 수 없음.
 */

@Slf4j
@Component
@RequiredArgsConstructor
public class WebSocketEventListener {

    private final DestinationMatcher subscribe;
    private final DestinationMatcher unsubscribe;
    private final DestinationMatcher disconnect;

    @PostConstruct
    public void init(){

        subscribe.add(new WatchingSessionJoinRule(Pattern.compile("^/sub/contents/test/watch$")));
        unsubscribe.add(new WatchingSessionLeaveRule(Pattern.compile("^/sub/contents/test/watch$")));
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
        subscribe.run(new SessionInfo(event.getMessage(), event.getUser()));
    }

    /**
     * Unsubscription 프레임을 수신한 직후 발생되는 이벤트.
     * ChannelInterceptor 가 preSend() 로 executor 에 작업을 등록 한 이후,
     * 동기적으로 발생 시키는 이벤트.
     * @param event 일반적인 메세지(세션, 구독)와, Principal 을 담고있음.
     */
    @EventListener
    public void onUnsubscribe(SessionUnsubscribeEvent event){ unsubscribe.run(new SessionInfo(event.getMessage(), event.getUser())); }


    /**
     * 웹 소켓 연결이 종료되고 난 후,
     * Disconnect 메세지를 ChannelInterceptor 에 "송신하기 전"에
     * 동기적으로 발생시키는 이벤트.
     * @param event 세션Id 와 closeStatus 를 담고 있음.
     */
    @EventListener
    public void onDisconnect(SessionDisconnectEvent event){ disconnect.run(new SessionInfo(event.getMessage(), event.getUser())); }


}
