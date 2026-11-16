package com.codeit.sb13.playlist_for_everyone.global.websocket.event;

import com.codeit.sb13.playlist_for_everyone.global.websocket.event.auth.WebSocketAuthenticateRevokeEvent;
import com.codeit.sb13.playlist_for_everyone.global.websocket.watching.event.WatchingSessionContentDeleteEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * 이벤트 발행 명세에 대한 팩토리 객체.
 * 웹 소켓 관련 이벤트의 발행을 중개하는 역할을 한다.
 */

@Component
@RequiredArgsConstructor
public class WebSocketEventFactory {
    private final ApplicationEventPublisher publisher;

    /**
     * 유저 정보가 변경되었을 때, Redis 를 이용해서 인스턴스에 이벤트를 발행하는 매서드
     * @param username 취소 되어야 할 인증의 Username.
     */
    public void authRevokedEvent(String username){
        publisher.publishEvent(new WebSocketAuthenticateRevokeEvent(username));
    }

    /**
     * 컨텐츠가 삭제 되었을 때, 삭제된 컨텐츠의 id 를 모든 인스턴스에게 발행하는 매서드
     * @param contentId 삭제된 컨텐츠의 id
     */
    public void contentDeleteEvent(UUID contentId) {
        publisher.publishEvent(new WatchingSessionContentDeleteEvent(contentId));
    }

}
