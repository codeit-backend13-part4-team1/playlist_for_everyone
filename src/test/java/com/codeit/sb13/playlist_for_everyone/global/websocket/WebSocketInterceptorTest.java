package com.codeit.sb13.playlist_for_everyone.global.websocket;


import com.codeit.sb13.playlist_for_everyone.domain.auth.jwt.JwtTokenProvider;
import com.codeit.sb13.playlist_for_everyone.domain.user.entity.Role;
import com.codeit.sb13.playlist_for_everyone.domain.user.entity.User;
import com.codeit.sb13.playlist_for_everyone.domain.user.service.UserService;
import com.codeit.sb13.playlist_for_everyone.global.service.WatcherRegistry;
import com.codeit.sb13.playlist_for_everyone.global.service.WatcherRegistryDelay;
import io.jsonwebtoken.JwtException;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class WebSocketInterceptorTest {

    @Mock
    WatcherRegistry watcherRegistry;
    @Mock
    MessageChannel messageChannel;
    @Mock
    JwtTokenProvider tokenProvider;
    @Mock
    UserService userService;
    @Spy
    WatcherRegistryDelay delay;
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
        accessor.setSubscriptionId("test-subscription");
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


    private void tokenSimulateSetup(String token){
        // 토큰이 있다고 가정한다.
        given(tokenProvider.parseAccessToken(eq(token))).willAnswer(
                inv -> {
                    String gettedToken = inv.getArgument(0);

                    if (!token.equals(gettedToken))
                        throw new JwtException("유효하지 않은 토큰입니다.");

                    return UUID.randomUUID();
                });

        given(userService.getById(any(UUID.class))).willAnswer(
                inv -> {
                    UUID id = inv.getArgument(0);
                    User user = User.builder()
                            .email("test@email.com")
                            .name("test")
                            .password("password")
                            .role(Role.USER)
                            .build();
                    ReflectionTestUtils.setField(user, "id", id);
                    return user;
                }
        );
    }


    @Nested
    @DisplayName("Connection 동작 테스트")
    class Connection {



        @Test
        @DisplayName("유효한 토큰이면 연결. 유저 정보가 메세지에 담겨나옴. 세션이 등록된다.")
        public void wrightTokenConnectionTest() throws Exception {
            StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.CONNECT);
            accessor.setNativeHeader("Authorization", "Bearer testToken");
            accessor.setLeaveMutable(true);

            Message<byte[]> message = MessageBuilder.createMessage(
                    new byte[2],
                    accessor.getMessageHeaders()
            );

            tokenSimulateSetup("testToken");

            Message<?> result = interceptor.preSend(message,messageChannel);


            assertThat(result).isNotNull();

            StompHeaderAccessor accessorResult = MessageHeaderAccessor.getAccessor(result, StompHeaderAccessor.class);

            assertThat(accessorResult.getUser()).isNotNull();
        }

        @Test
        @DisplayName("Bearer 형식이 아닐경우, 인증 실패")
        public void wrongTokenConnectionTest() throws Exception {
            StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.CONNECT);
            accessor.setNativeHeader("Authorization", "testToken");
            accessor.setLeaveMutable(true);

            Message<byte[]> message = MessageBuilder.createMessage(
                    new byte[2],
                    accessor.getMessageHeaders()
            );

            assertThatThrownBy(
                    () -> interceptor.preSend(message,messageChannel)
            ).isInstanceOf(BadCredentialsException.class);
        }

        @Test
        @DisplayName("토큰이 없다면 연결 실패")
        public void noTokenConnectionTest(){
            Message<byte[]> message = createMessage(
                    StompCommand.CONNECT,
                    null,
                    "/ws",
                    new byte[2]
            );

            assertThatThrownBy(
                    () -> interceptor.preSend(message,messageChannel)
            ).isInstanceOf(BadCredentialsException.class); // todo - 추후 인증 실패 예외로 바꾸기.

            // token 공급자 상호작용 없음.
        }
    }

    @Nested
    @DisplayName("Subscribe 동작 테스트")
    class Subscribe {
        private StompHeaderAccessor subscriptionHeader(String destination){
            StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
            accessor.setSessionId("test-session-1");
            accessor.setSubscriptionId("test-subscription-1");
            accessor.setDestination(destination);
            accessor.setLeaveMutable(true);
            return accessor;
        }

        @Test
        @DisplayName("컨텐츠 동시 시청 경로일 시 컨텐츠 아이디를 저장한다.")
        public void registerIdTest(){
            String mockupContentId = UUID.randomUUID().toString();
            String destination = "/sub/contents/" + mockupContentId + "/watch";
            Message<byte[]> message = MessageBuilder.createMessage(
                    new byte[2],
                    subscriptionHeader(destination).getMessageHeaders()
            );

            interceptor.preSend(message,messageChannel);

            verify(watcherRegistry,times(1)).register(any(String.class),any(String.class),eq(mockupContentId));
        }

        @Test
        @DisplayName("컨텐츠 동시 시청 경로가 아닐 시 컨텐츠 아이디를 저장하지 않는다.")
        public void notRegisterIdTest(){
            String mockupContentId = UUID.randomUUID().toString();
            String destination = "/sub/contents/" + mockupContentId + "/chat";
            Message<byte[]> message = MessageBuilder.createMessage(
                    new byte[2],
                    subscriptionHeader(destination).getMessageHeaders()
            );
            interceptor.preSend(message,messageChannel);


            verify(watcherRegistry,never()).register(any(String.class),any(String.class),eq(mockupContentId));
        }

        @Test
        @DisplayName("경로가 맞다 하더라도, 아이디 형식이 지정된 형식(UUID) 이 아니라면 저장하지 않는다.")
        public void notRegisterIdTest2(){
            String mockupContentId = "fhjenof";
            String destination = "/sub/contents/" + mockupContentId + "/watch";
            Message<byte[]> message = MessageBuilder.createMessage(
                    new byte[2],
                    subscriptionHeader(destination).getMessageHeaders()
            );

            interceptor.preSend(message,messageChannel);


            verify(watcherRegistry,never()).register(any(String.class),any(String.class),eq(mockupContentId));
        }

    }

    @Test
    @DisplayName("하트비트 테스트")
    void heartbeatTest(){
        String mockupSessionId = UUID.randomUUID().toString();

        StompHeaderAccessor headerAccessor = StompHeaderAccessor.createForHeartbeat();
        headerAccessor.setSessionId(mockupSessionId);

        Message<byte[]> message = MessageBuilder
                .createMessage(new byte[]{'\n'},headerAccessor.getMessageHeaders());

        interceptor.preSend(message,messageChannel);

        verify(watcherRegistry,times(1)).refresh(eq(mockupSessionId));
    }

}
