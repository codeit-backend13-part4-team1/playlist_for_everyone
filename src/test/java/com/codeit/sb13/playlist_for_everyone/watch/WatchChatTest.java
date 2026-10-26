package com.codeit.sb13.playlist_for_everyone.watch;


import com.codeit.sb13.playlist_for_everyone.global.config.WebSocketConfig;
import com.codeit.sb13.playlist_for_everyone.global.service.WatcherRegistry;
import com.codeit.sb13.playlist_for_everyone.global.websocket.WebSocketController;
import com.codeit.sb13.playlist_for_everyone.global.websocket.WebSocketInterceptor;
import com.codeit.sb13.playlist_for_everyone.global.websocket.dto.event.WebSocketChatEvent;
import com.codeit.sb13.playlist_for_everyone.global.websocket.dto.event.WebSocketContentWatchEvent;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.util.MimeTypeUtils;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.Mockito.*;

@Slf4j
@WebMvcTest(controllers = {WebSocketController.class})
@Import({
        WebSocketConfig.class,
        WebSocketInterceptor.class
})
@RecordApplicationEvents
public class WatchChatTest {

    @Autowired
    @Qualifier("clientInboundChannel")
    private MessageChannel inbound;

    @MockitoBean
    private WatcherRegistry watcherRegistry;

    @Autowired
    private ApplicationEvents applicationEvents;

    @MockitoSpyBean
    private SimpMessagingTemplate simpMessagingTemplate;

    private final String PUB_PREFIX = "/pub/contents/";
    private final String SUB_PREFIX = "/sub/contents/";
    private final String CHAT_POSTFIX = "/chat";
    private final String WATCH_POSTFIX = "/watch";


    private String pathResolver(String preFix, UUID contentId, String postFix){
        return preFix + contentId + postFix;
    }

    private StompHeaderAccessor stompHeader(
            StompCommand command,
            String destination
    ){
        StompHeaderAccessor accessor = StompHeaderAccessor.create(command);
        accessor.setDestination(destination);
        accessor.setContentType(MimeTypeUtils.APPLICATION_JSON);
        accessor.setSessionId("test");
        accessor.setSessionAttributes(new HashMap<>());
        return accessor;
    }



    @Test
    @DisplayName("공통 채널 구독시 채널 정보 반환")
    void contentSubscriptionTest(){
        UUID contentId = UUID.randomUUID();
        StompHeaderAccessor accessor = stompHeader(
                StompCommand.SUBSCRIBE,
                pathResolver(SUB_PREFIX, contentId, WATCH_POSTFIX));
        Message<byte[]> message = MessageBuilder.createMessage(
                "".getBytes(StandardCharsets.UTF_8),
                accessor.toMessageHeaders()
        );
        inbound.send(message);

        await().atMost(Duration.ofSeconds(3))
                .untilAsserted(() -> {

                    List<WebSocketContentWatchEvent> events =
                            applicationEvents
                                    .stream(WebSocketContentWatchEvent.class)
                                    .toList();

                    assertThat(events).hasSize(1);

                    WebSocketContentWatchEvent event = events.get(0);
                    log.info("[WebSocketChatTest] 이벤트 발생 - event: {}", event);
                    assertThat(event).isNotNull();
                });
    }


    @Test
    @DisplayName("메세지 전송시 전송 이벤트 발생.")
    void chatTest(){
        String chat = """
                {
                    "content": "test"
                }
                """;
        UUID contentId = UUID.randomUUID();

        StompHeaderAccessor accessor = stompHeader(
                StompCommand.SEND,
                pathResolver(PUB_PREFIX, contentId, CHAT_POSTFIX));

        Message<byte[]> message = MessageBuilder.createMessage(
                chat.getBytes(StandardCharsets.UTF_8),
                accessor.toMessageHeaders()
        );

        inbound.send(message);

        await().atMost(Duration.ofSeconds(1))
                .untilAsserted(() -> {

                    List<WebSocketChatEvent> events =
                            applicationEvents
                                    .stream(WebSocketChatEvent.class)
                                    .toList();

                    assertThat(events).hasSize(1);

                    WebSocketChatEvent event = events.get(0);
                    log.info("[WebSocketChatTest] 이벤트 발생 - event: {}", event);
                    assertThat(event).isNotNull();
                });
    }


}
