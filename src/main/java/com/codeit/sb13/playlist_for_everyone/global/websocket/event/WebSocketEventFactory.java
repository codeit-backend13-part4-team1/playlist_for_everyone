package com.codeit.sb13.playlist_for_everyone.global.websocket.event;

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
     * 컨텐츠가 삭제 되었을 때, 삭제된 컨텐츠의 id 를 모든 인스턴스에게 발행하는 매서드
     * @param contentId 삭제된 컨텐츠의 id
     */
    public void contentDeleteEvent(UUID contentId) {
        publisher.publishEvent(new WatchingSessionContentDeleteEvent(contentId));
    }

}
