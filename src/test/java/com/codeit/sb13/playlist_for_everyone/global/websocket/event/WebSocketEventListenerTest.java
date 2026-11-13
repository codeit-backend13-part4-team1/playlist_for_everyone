package com.codeit.sb13.playlist_for_everyone.global.websocket.event;


import com.codeit.sb13.playlist_for_everyone.global.websocket.watching.WatchingSession;
import com.codeit.sb13.playlist_for_everyone.global.websocket.watching.event.WatchingSessionContentDeleteEvent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@SpringBootTest
public class WebSocketEventListenerTest {

    @Autowired
    ApplicationEventPublisher applicationEventPublisher;

    @MockitoBean
    WatchingSession watchingSession;

    @Test
    @DisplayName("컨텐츠 삭제시 이벤트 호출 1회만 발생 하는지 테스트")
    void contentDeleteEventTest(){
        WatchingSessionContentDeleteEvent event = new WatchingSessionContentDeleteEvent(UUID.randomUUID());

        applicationEventPublisher.publishEvent(event);

        verify(watchingSession, times(1)).disconnectWithContentId(eq(event.contentId()));
    }


}
