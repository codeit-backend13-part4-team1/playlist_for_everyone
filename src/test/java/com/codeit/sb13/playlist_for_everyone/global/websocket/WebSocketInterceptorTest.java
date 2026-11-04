package com.codeit.sb13.playlist_for_everyone.global.websocket;


import com.codeit.sb13.playlist_for_everyone.domain.auth.jwt.JwtTokenProvider;
import com.codeit.sb13.playlist_for_everyone.domain.user.entity.Role;
import com.codeit.sb13.playlist_for_everyone.domain.user.entity.User;
import com.codeit.sb13.playlist_for_everyone.domain.user.service.UserService;
import com.codeit.sb13.playlist_for_everyone.global.service.lock.SpreadLock;
import com.codeit.sb13.playlist_for_everyone.global.websocket.matcher.DestinationMatcher;
import com.codeit.sb13.playlist_for_everyone.global.websocket.matcher.SessionMatcher;
import com.codeit.sb13.playlist_for_everyone.global.websocket.registry.ConnectionRegistry;
import io.jsonwebtoken.JwtException;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.*;
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
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

/**
 * 채널 인터셉터 모듈 테스트.
 * 인증 정보를 메세지 안에 잘 담는지,
 * 하트비트를 잘 받는지
 * 직접 클래스를 올려서 테스트.
 */

@Slf4j
@ExtendWith(MockitoExtension.class)
public class WebSocketInterceptorTest {

    @Mock
    MessageChannel messageChannel;
    @Mock
    JwtTokenProvider tokenProvider;
    @Mock
    UserService userService;
    @Mock
    HeartBeatDelay delay;
    @Mock
    ConnectionRegistry connectionRegistry;
    @Mock
    SessionMatcher sessionMatcher;
    @Mock
    DestinationMatcher destinationMatcher;

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


    private Message<byte[]> getMessage(
            StompCommand command,
            String sessionId,
            String authToken
    ){
        StompHeaderAccessor accessor = StompHeaderAccessor.create(command);
        accessor.setSessionId(sessionId);
        accessor.setSubscriptionId("test-sub");
        if (authToken != null) {
        accessor.setNativeHeader("Authorization", authToken);
        }
        accessor.setLeaveMutable(true);
        return MessageBuilder.createMessage(
                new byte[2],
                accessor.getMessageHeaders()
        );
    }

    @BeforeEach
    public void setup() {
        // 테스트를 위한 딜레이 객체 교체
        ReflectionTestUtils.setField(
                interceptor,
                "delay",
                new HeartBeatDelay(2000L,new SpreadLock())); // 딜레이 텀
    }


    @Nested
    @DisplayName("Connection 동작 테스트")
    class Connection {
        @Test
        @DisplayName("유효한 토큰이면 연결. 유저 정보가 메세지에 담겨나옴. 세션이 등록된다.")
        public void wrightTokenConnectionTest() throws Exception {
            Message<?> message = getMessage(StompCommand.CONNECT, "testSession", "Bearer " + "testToken");

            tokenSimulateSetup("testToken");

            Message<?> result = interceptor.preSend(message,messageChannel);

            assertThat(result).isNotNull();

            StompHeaderAccessor accessorResult = MessageHeaderAccessor.getAccessor(result, StompHeaderAccessor.class);

            assertThat(accessorResult.getUser()).isNotNull();
        }

        @Test
        @DisplayName("Bearer 형식이 아닐경우, 인증 실패")
        public void wrongTokenConnectionTest() throws Exception {
            Message<?> message = getMessage(StompCommand.CONNECT, "testSession", "testToken");

            assertThatThrownBy(
                    () -> interceptor.preSend(message,messageChannel)
            ).isInstanceOf(BadCredentialsException.class);
        }

        @Test
        @DisplayName("토큰이 없다면 연결 실패")
        public void noTokenConnectionTest(){
            Message<?> message = getMessage(StompCommand.CONNECT, "testSession", null);

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
            accessor.setSubscriptionId("test-subscribe-1");
            accessor.setDestination(destination);
            accessor.setLeaveMutable(true);
            return accessor;
        }

        @Test
        @DisplayName("connectionRegistry 에 현재 세션 정보를 저장한다.")
        public void registerIdTest(){
            String mockupContentId = UUID.randomUUID().toString();
            String destination = "/sub/contents/" + mockupContentId + "/watch";
            Message<byte[]> message = MessageBuilder.createMessage(
                    new byte[2],
                    subscriptionHeader(destination).getMessageHeaders()
            );

            interceptor.preSend(message,messageChannel);

            // 세션 정보 저장 매서드가 호출됨.
            verify(connectionRegistry,times(1))
                    .register(any(SessionData.class));

            List<SessionData> sessiondDataList = mockingDetails(connectionRegistry)
                    .getInvocations()
                    .stream()
                    .filter(inv -> inv.getMethod().getName().equals("register"))
                    .map(inv -> (SessionData) inv.getArguments()[0])
                    .toList();

            assertThat(sessiondDataList).hasSize(1);
            assertThat(sessiondDataList.get(0).session()).isEqualTo("test-session-1");
            assertThat(sessiondDataList.get(0).subscription()).isEqualTo("test-subscribe-1");
            assertThat(sessiondDataList.get(0).destination()).isEqualTo(destination);

        }
    }

    @Nested
    @DisplayName("구독 취소 테스트")
    class unsubscribeTest {

        @Test
        @DisplayName("구독 취소 시, connectionRegistry 에서 정보를 취소한다.")
        void unsubscribeTest1(){
            Message<?> message = getMessage(StompCommand.UNSUBSCRIBE, "testSession", null);

            interceptor.preSend(message,messageChannel);

            verify(connectionRegistry,times(1)).unregister(any(SessionData.class));
            List<SessionData> sessiondDataList = mockingDetails(connectionRegistry)
                    .getInvocations()
                    .stream()
                    .filter(inv -> inv.getMethod().getName().equals("unregister"))
                    .map(inv -> (SessionData) inv.getArguments()[0])
                    .toList();

            assertThat(sessiondDataList).hasSize(1);
            assertThat(sessiondDataList.get(0).session()).isEqualTo("testSession");
            assertThat(sessiondDataList.get(0).subscription()).isEqualTo("test-sub");
        }
    }

    @Nested
    @DisplayName("구독 삭제 테스트")
    class disconnectTest {

        @Test
        @DisplayName("연결 끊김시, connectionRegistry 에서 정보를 취소한다.")
        void disconnectTest1(){
            Message<?> message = getMessage(StompCommand.DISCONNECT, "testSession", null);

            interceptor.preSend(message,messageChannel);

            verify(connectionRegistry,times(1)).unregister(any(SessionData.class));
            List<SessionData> sessiondDataList = mockingDetails(connectionRegistry)
                    .getInvocations()
                    .stream()
                    .filter(inv -> inv.getMethod().getName().equals("unregister"))
                    .map(inv -> (SessionData) inv.getArguments()[0])
                    .toList();

            assertThat(sessiondDataList).hasSize(1);
            assertThat(sessiondDataList.get(0).session()).isEqualTo("testSession");
        }
    }

    @Nested
    @DisplayName("하트비트 테스트")
    class heartbeatTest {

        Message<byte[]> getHeartbeat(){
            String mockupSessionId = UUID.randomUUID().toString();

            StompHeaderAccessor headerAccessor = StompHeaderAccessor.createForHeartbeat();
            headerAccessor.setSessionId(mockupSessionId);

            return MessageBuilder
                    .createMessage(new byte[]{'\n'},headerAccessor.getMessageHeaders());

        }

        @Test
        @DisplayName("하트비트 테스트 - 하트비트 매쳐를 정상적으로 작동 시키는지 테스트")
        void heartbeatTest1(){
            Message<byte[]> message = getHeartbeat();

            interceptor.preSend(message,messageChannel);

            // 하트비트 매쳐 작동 확인
            verify(sessionMatcher,times(1)).run(any(SessionInfo.class));
        }

        @Test
        @DisplayName("하트비트 테스트 - 딜레이 타입이 지나면, 정상적으로 다시 실행된다.")
        void heartbeatTest4(){
            Message<byte[]> message = getHeartbeat();
            log.info("하트비트 테스트 4 - 시작");
            interceptor.preSend(message,messageChannel);

            try{
                Thread.sleep(2000);
            } catch (InterruptedException e) {
                log.info("하트비트 테스트 4 - 에러", e);
            }

            interceptor.preSend(message,messageChannel);
            log.info("하트비트 테스트 4 - 끝");
            // 하트비트 매쳐 작동 확인
            verify(sessionMatcher,times(2)).run(any(SessionInfo.class));
        }

        @Test
        @DisplayName("하트비트 테스트 - 메세지가 동시에 여러개 도착해도, 하트비트 로직은 한번만 실행된다.")
        void heartbeatTest2(){
            Message<byte[]> message = getHeartbeat();
            for(int i=0;i<10;i++){
                log.info("하트비트 큐");
                interceptor.preSend(message,messageChannel);
            }

            // 하트비트 매쳐 작동 확인
            verify(sessionMatcher,times(1)).run(any(SessionInfo.class));
        }

        @Test
        @DisplayName("하트비트 테스트 - 서로 다른 세션은 각각의 딜레이를 가진다.")
        void heartbeatTest3(){
            Message<byte[]> message = getHeartbeat();
            Message<byte[]> message2 = getHeartbeat();

            for(int i=0;i<10;i++){
                log.info("하트비트 큐");
                if(i < 7)
                    interceptor.preSend(message,messageChannel);
                else
                    interceptor.preSend(message2,messageChannel);
            }

            // 하트비트 매쳐 작동 확인
            verify(sessionMatcher,times(2)).run(any(SessionInfo.class));
        }

    }


}
