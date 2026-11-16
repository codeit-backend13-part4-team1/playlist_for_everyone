package com.codeit.sb13.playlist_for_everyone.global.websocket.registry.connection;

import com.codeit.sb13.playlist_for_everyone.global.websocket.SessionInfo;
import com.codeit.sb13.playlist_for_everyone.global.websocket.registry.subscription.SubscriptionRegistryImpl;
import com.codeit.sb13.playlist_for_everyone.global.websocket.registry.session.WebSocketRegistry;
import com.codeit.sb13.playlist_for_everyone.global.websocket.registry.subscription.SubscriptionRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.Message;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.web.socket.WebSocketSession;

import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

/*
 * 세션 - 구독 키 쌍을 저장하는 구현체를 직접 호출해서 테스트 한다.
 * 세션 - 구독 키 쌍을 나타내는 SocketConnection 객체는 필드가 변환되지 않으므로, 동시성 테스트는 진행하지 않는다.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("세션-커넥션 키 쌍 테스트")
public class SubscriptionRegistryTest {

    @Mock
    WebSocketRegistry socketRegistry;

    SubscriptionRegistry subscriptionRegistry;

    SessionInfo getSessionInfo(
            String sessionId,
            String subscriptionId,
            String destination
    ){
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
        accessor.setSessionId(sessionId);
        accessor.setSubscriptionId(subscriptionId);
        accessor.setDestination(destination);


        Message<byte[]> message = MessageBuilder.createMessage(
                "this is payload".getBytes(StandardCharsets.UTF_8),
                accessor.getMessageHeaders()
        );
        return new SessionInfo(message);
    }

    void setSocketRegistryHasSession(String sessionId){
        given(socketRegistry.query(sessionId))
                .willReturn(Optional.of(mock(WebSocketSession.class)));
    }

    @BeforeEach
    void setUp(){
        subscriptionRegistry = new SubscriptionRegistryImpl(socketRegistry);
    }

    static String sessionId = "test-session-id";
    static String subscriptionId = "test-subscription-id";
    static String destination = "/destination/path";

    @Nested
    @DisplayName("등록 테스트")
    class Register {

        static Stream<Arguments> paramNullTestArgv(){
            return Stream.of(
                    Arguments.of(null, subscriptionId, destination),
                    Arguments.of(sessionId, null, destination)
            );
        }

        @Test
        @DisplayName("현재 세션 정보를 등록하고 조회하면, 구독 정보가 SessionInfo 객체로 반환된다.")
        void saveSession(){
            SessionInfo info = getSessionInfo(
                    sessionId,
                    subscriptionId,
                    destination);

            setSocketRegistryHasSession(sessionId);

            subscriptionRegistry.register(info);

            Optional<SessionInfo> connection = subscriptionRegistry.get(info);

            assertThat(connection.isEmpty()).isFalse();
            assertThat(connection.get().session()).isEqualTo("test-session-id");
            assertThat(connection.get().subscription()).isEqualTo("test-subscription-id");
            assertThat(connection.get().destination()).isEqualTo("/destination/path");
        }

        @ParameterizedTest
        @MethodSource("paramNullTestArgv")
        @DisplayName("SessionInfo 에 세션, 혹은 구독 정보 없이 등록하면 NPE 를 반환한다.")
        void saveSessionWithoutSession(String sessionId, String subscriptionId, String destination){
            SessionInfo info = getSessionInfo(sessionId, subscriptionId, destination);

            if (sessionId != null) setSocketRegistryHasSession(sessionId);

            assertThatThrownBy(() -> subscriptionRegistry.register(info))
                    .isInstanceOf(NullPointerException.class);
        }

        @Test
        @DisplayName("동일한 세션 - 구독 키를 복수회 등록하면 최초의 세션 정보만 등록된다.")
        void saveDurationSessionWithSameKey(){
            // session 과 id 가 동일하다면 같은 구독으로 간주된다.
            SessionInfo info = getSessionInfo(sessionId, subscriptionId, destination);
            SessionInfo info2 = getSessionInfo(sessionId, subscriptionId, null);
            SessionInfo info3 = getSessionInfo(sessionId, subscriptionId, "sdf");

            setSocketRegistryHasSession(sessionId);

            subscriptionRegistry.register(info);
            subscriptionRegistry.register(info2);
            subscriptionRegistry.register(info3);


            assertThat(subscriptionRegistry.getAllBySession(info))
                    .hasSize(1)
                    .contains(info);
        }
    }



    // 조회 테스트

    @Nested
    @DisplayName("등록 해제 테스트")
    class Unregister {

        String subscriptionId2 = "test-subscription-id2";
        String destination2 = "test-destination2";

        SessionInfo info = getSessionInfo(sessionId, subscriptionId, destination);
        SessionInfo info2 = getSessionInfo(sessionId, subscriptionId2, destination2);

        static Stream<Arguments> paramNullTestArgv(){
            return Stream.of(
                    Arguments.of(null, subscriptionId, destination),
                    Arguments.of(sessionId, null, destination)
            );
        }

        @BeforeEach
        void setUp(){
            setSocketRegistryHasSession(sessionId);

            subscriptionRegistry.register(info);
            subscriptionRegistry.register(info2);
        }

        @Test
        @DisplayName("현재 등록된 세션을 해제 할 수 있다.")
        void removeSession(){
            SessionInfo info3 = getSessionInfo(sessionId, subscriptionId, destination);

            assertThat(subscriptionRegistry.getAllBySession(info3))
                    .hasSize(2)
                    .contains(info, info2);

            subscriptionRegistry.unregister(info3);

            assertThat(subscriptionRegistry.getAllBySession(info3))
                    .hasSize(1)
                    .contains(info2);
        }

        @ParameterizedTest
        @MethodSource("paramNullTestArgv")
        @DisplayName("세션, 혹은 구독 id 가 존재하지 않으면 NPE를 반환한다..")
        void removeSession2(String sessionId, String subscriptionId, String destination){
            SessionInfo info = getSessionInfo(sessionId, subscriptionId, destination);

            if (sessionId != null) setSocketRegistryHasSession(sessionId);

            assertThatThrownBy(() -> subscriptionRegistry.register(info))
                    .isInstanceOf(NullPointerException.class);
        }

        @Test
        @DisplayName("disconnect 매서드는 세션에 대한 모든 구독을 삭제한다")
        void removeSession3(){
            SessionInfo info3 = getSessionInfo(sessionId, subscriptionId, destination);

            assertThat(subscriptionRegistry.getAllBySession(info3))
                    .hasSize(2)
                    .contains(info, info2);

            subscriptionRegistry.disconnect(info3);

            assertThat(subscriptionRegistry.getAllBySession(info3))
                    .isEmpty();
        }
    }

    @Nested
    @DisplayName("조회 테스트")
    class GetSession {

        String subscriptionId2 = "test-subscription-id2";

        SessionInfo info = getSessionInfo(sessionId, subscriptionId, "this/is/sub/1");
        SessionInfo info2 = getSessionInfo(sessionId, subscriptionId2, "this/is/sub/2");

        static Stream<Arguments> paramNullTestArgv(){
            return Stream.of(
                    Arguments.of(null, subscriptionId, destination),
                    Arguments.of(sessionId, null, destination)
            );
        }

        @BeforeEach
        void setUp(){
            setSocketRegistryHasSession(sessionId);
            subscriptionRegistry.register(info);
            subscriptionRegistry.register(info2);
        }

        @Test
        @DisplayName("현재 구독 정보를 조회하면, 경로 정보가 포함된 구독 정보를 반환한다.")
        void getSession(){
            SessionInfo info3 = getSessionInfo(sessionId, subscriptionId, null);

            setSocketRegistryHasSession(sessionId);

            SessionInfo subscriptionInfo = subscriptionRegistry.get(info3)
                    .orElseThrow(() -> new RuntimeException("테스트 에러! 구독이 존재하지 않음."));

            assertThat(subscriptionInfo.destination()).isEqualTo("this/is/sub/1");
        }

        @Test
        @DisplayName("세션에 대해 조회하면, 세션에 대한 모든 구독 정보를 반환한다.")
        void getSessionList(){
            SessionInfo info3 = getSessionInfo(sessionId, subscriptionId, null);

            setSocketRegistryHasSession(sessionId);

            assertThat(subscriptionRegistry.getAllBySession(info3))
                    .hasSize(2);
        }
    }
}
