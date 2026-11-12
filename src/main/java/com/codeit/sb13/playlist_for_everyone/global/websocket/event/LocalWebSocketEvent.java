package com.codeit.sb13.playlist_for_everyone.global.websocket.event;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;

/**
 * 로컬 인스턴스에 대해 작동하는 이벤트를 발행, 처리하는 클래스
 */
public abstract class LocalWebSocketEvent implements WebSocketEvent {
    private final ApplicationEventPublisher publisher;

    public LocalWebSocketEvent(ApplicationEventPublisher publisher) {
        this.publisher = publisher;
    }

    public void publish(Object data) {
        publisher.publishEvent(data);
    }

    @EventListener
    protected void onEvent(Object data) {
        run(data);
    }

    /**
     * 이벤트로 발행된 데이터 객체를 가지고
     * 이벤트에 해당하는 로직을 수행하는 매서드.
     * @param data 이벤트에 의해 전달 될 데이터 객체.
     */
    abstract void run(Object data);

}
