package com.codeit.sb13.playlist_for_everyone.global.websocket.event;

import com.codeit.sb13.playlist_for_everyone.domain.auth.security.SecurityUser;
import com.codeit.sb13.playlist_for_everyone.global.service.redis.RedisBrokerRegister;
import com.codeit.sb13.playlist_for_everyone.global.websocket.watching.event.WatchingSessionContentDeletedEvent;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * 이벤트 발행 명세에 대한 팩토리 객체.
 * 이벤트에 대한 브로커 등록 및 이벤트 자체의 발행을 중개하는 역할을 가진다.
 * 이벤트를 발행할 때, 필요한 데이터 역시 받아서 퍼블리싱 한다.
 */

@Component
@RequiredArgsConstructor
public class WebSocketEventFactory {
    private final RedisBrokerRegister broker;
    private final WebSocketAuthenticateRevokeEvent authRevokeEvent;
    private final WatchingSessionContentDeletedEvent contentDeleteEvent;

    /**
     * 서버가 시동될 떄, 이벤트를 브로커에 등록해서 Redis 관련 이벤트를 발행 할 수 있게하는 매서드.
     */
    @PostConstruct
    private void setup(){
        broker.register(authRevokeEvent,authRevokeEvent.topic());
        broker.register(contentDeleteEvent,contentDeleteEvent.topic());
    }

    /**
     * 유저 정보가 변경되었을 때, Redis 를 이용해서 인스턴스에 이벤트를 발행하는 매서드
     * @param username 취소 되어야 할 인증의 Username.
     */
    public void authRevokedEvent(String username){
        authRevokeEvent.publish(username);
    }

    public void contentDeleteEvent(UUID contentId) {
        contentDeleteEvent.publish(contentId);
    }

}
