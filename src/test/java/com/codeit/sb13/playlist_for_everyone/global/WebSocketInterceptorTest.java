package com.codeit.sb13.playlist_for_everyone.global;


import com.codeit.sb13.playlist_for_everyone.global.service.WatcherRegistry;
import com.codeit.sb13.playlist_for_everyone.global.websocket.WebSocketInterceptor;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.messaging.support.MessageHeaderAccessor;

import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class WebSocketInterceptorTest {

    @Mock
    WatcherRegistry watcherRegistry;
    @Mock
    private MessageChannel messageChannel;
    @InjectMocks
    private WebSocketInterceptor interceptor;

    private Message<byte[]> createMessage (
            StompCommand command,
            String authToken,
            String destination,
            byte[] payload
    ) {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(command);

        accessor.setSessionId("test-session");
        accessor.setDestination(destination);
        if (authToken != null) {
            accessor.setHeader("Authorization", authToken);
        }

        // 메세지가 전송 채널 안에서 수정 가능하게 해줌.
        accessor.setLeaveMutable(true);

        return MessageBuilder.createMessage(
                payload,
                accessor.getMessageHeaders()
        );
    }




    @Nested
    @DisplayName("Connection 동작 테스트")
    class Connection {

        @Disabled // 인증 부분이 완성되면 활성
        @Test
        @DisplayName("유효한 토큰이면 연결.")
        public void wrightTokenConnectionTest(){
            Message<byte[]> message = createMessage(
                    StompCommand.CONNECT,
                    null,   // todo - 토큰 삽입
                    "/ws",
                    new byte[2]
            );

            Message<?> result = interceptor.preSend(message,messageChannel);

            StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);

            // todo - 토큰 추가 후 사용자 검정부분 작성
//            assertThat(accessor.getUser()).isSameAs();

        }

        @Test
        @DisplayName("토큰이 없거나 유효한 토큰이 아니면 연결 실패")
        public void wrongTokenConnectionTest(){
            Message<byte[]> message = createMessage(
                    StompCommand.CONNECT,
                    null,
                    "/ws",
                    new byte[2]
            );

            assertThatThrownBy(
                    () -> interceptor.preSend(message,messageChannel)
            ).isInstanceOf(RuntimeException.class); // todo - 추후 인증 실패 예외로 바꾸기.

            // token 공급자 상호작용 없음.
        }
    }

    @Nested
    @DisplayName("Subscribe 동작 테스트")
    class Subscribe {

        @Test
        @DisplayName("컨텐츠 동시 시청 경로일 시 컨텐츠 아이디를 저장한다.")
        public void registerIdTest(){
            String mockupContentId = UUID.randomUUID().toString();

            Message<byte[]> message = createMessage(
                    StompCommand.SUBSCRIBE,
                    null,
                    "/sub/contents/" + mockupContentId + "/watch",
                    new byte[2]
            );

            interceptor.preSend(message,messageChannel);

            verify(watcherRegistry,times(1)).register(eq(mockupContentId),any(String.class));
        }

        @Test
        @DisplayName("컨텐츠 동시 시청 경로가 아닐 시 컨텐츠 아이디를 저장하지 않는다.")
        public void notRegisterIdTest(){
            String mockupContentId = UUID.randomUUID().toString();

            Message<byte[]> message = createMessage(
                    StompCommand.SUBSCRIBE,
                    null,
                    "/sub/contents/" + mockupContentId + "/chat",
                    new byte[2]
            );

            interceptor.preSend(message,messageChannel);


            verify(watcherRegistry,never()).register(eq(mockupContentId),any(String.class));
        }

        @Test
        @DisplayName("경로가 맞다 하더라도, 아이디 형식이 지정된 형식(UUID) 이 아니라면 저장하지 않는다.")
        public void notRegisterIdTest2(){
            String mockupContentId = "Afd134ds3s";

            Message<byte[]> message = createMessage(
                    StompCommand.SUBSCRIBE,
                    null,
                    "/sub/contents/" + mockupContentId + "/chat",
                    new byte[2]
            );

            interceptor.preSend(message,messageChannel);


            verify(watcherRegistry,never()).register(eq(mockupContentId),any(String.class));
        }

    }

}
