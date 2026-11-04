package com.codeit.sb13.playlist_for_everyone.global.websocket.watch.service;


import com.codeit.sb13.playlist_for_everyone.domain.auth.security.SecurityUser;
import com.codeit.sb13.playlist_for_everyone.domain.user.entity.Role;
import com.codeit.sb13.playlist_for_everyone.global.exception.EveryOneException;
import com.codeit.sb13.playlist_for_everyone.global.service.redis.RedisMessageListener;
import com.codeit.sb13.playlist_for_everyone.global.service.redis.RedisTopicRegistry;
import com.codeit.sb13.playlist_for_everyone.global.websocket.SessionInfo;
import com.codeit.sb13.playlist_for_everyone.global.websocket.watching.repository.WatchingSessionRepository;
import com.codeit.sb13.playlist_for_everyone.global.websocket.watching.service.WatchingSessionService;
import com.codeit.sb13.playlist_for_everyone.global.websocket.watching.service.WatchingSessionServiceImpl;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.Message;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;

import java.util.UUID;

import static com.codeit.sb13.playlist_for_everyone.global.websocket.watching.config.WatchingSessionProperties.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.BDDMockito.*;

/**
 * 동시 시청 서비스 클래스 테스트. 다음과 같은 역할을 가진다.
 * 1. 세션 정보를 받아와서 서비스에 사용 될 topic 을 생성한다.
 * 2. 세션 정보를 받아와서 세션 참가 / 이탈 과정을 처리한다.
 * 다른 도메인이나, 의존성 요소들은 각 요소들의 모듈 테스트로 검증한다.
 * 메세지 발신 밑 분산 인스턴스와의 통신은 event 리스너 단 통합 테스트에서 검증한다.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("WatchingSessionService 모듈 테스트")
public class WatchingSessionServiceTest {

    WatchingSessionService service;
    @Mock WatchingSessionRepository repository;
    @Mock
    RedisTopicRegistry redisTopicRegistry;

    /**
     * 테스트를 위한 SessionInfo 제작 매서드
     */

    SessionInfo getSessionInfo(
            String sessionId,
            String subscriptionId,
            String destination,
            Role role
    ){
        return getSessionInfo(sessionId, subscriptionId, destination, role, false);
    }

    SessionInfo getSessionInfo(
            String sessionId,
            String subscriptionId,
            String destination,
            Role role,
            Boolean isLocked
    ){
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.STOMP);

        accessor.setSessionId(sessionId);
        accessor.setSubscriptionId(subscriptionId);
        accessor.setDestination(destination);
        accessor.setLeaveMutable(true);

        Message<byte[]> message =  MessageBuilder.createMessage(
                new byte[0],
                accessor.getMessageHeaders()
        );

        SessionInfo info = new SessionInfo(message);

        SecurityUser user = new SecurityUser(
                UUID.randomUUID(),
                "test@email.com",
                "",
                role,
                isLocked
        );
        UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities());
        info.principal(authentication);

        return info;
    }


    /**
     * redis 구독 등록을 위한 테스트용 편의 매서드
     */
    // 테스트용 서비스 타입
    final String WATCH_SERVICE = "watch";
    final String CHAT_SERVICE = "chat";

    String testDestination(String contentId, String serviceType){
        return switch (serviceType) {
            case WATCH_SERVICE -> "/sub/contents/" + contentId + "/watch";
            case CHAT_SERVICE -> "/sub/contents/" + contentId + "/chat";
            default -> null;
        };
    }

    String assertionTopicAnswer(String contentId, String serviceType){
        return switch (serviceType){
            case WATCH_SERVICE -> MESSAGE_VIEW_PREFIX + contentId;
            case CHAT_SERVICE -> MESSAGE_CHAT_PREFIX + contentId;
            default -> null;
        };
    }

    /**
     * 테스트를 위한 stub 정의 매서드
     */

    void repositoryLogicSet(String contentId, Long watcherCount){
        given(repository.getWatcherCount(eq(contentId)))
                .willReturn(watcherCount);
    }

    @BeforeEach
    void setUp(){
        service = new WatchingSessionServiceImpl(repository);
    }

    @Test
    @DisplayName("세션 정보를 받아서, redis 메세지를 전송하기 위한 topic 을 반환한다.")
    void watchTopicTest() {
        String contentId = UUID.randomUUID().toString();
        String sessionId = "topic-test-session";

        SessionInfo viewerSessionInfo = getSessionInfo(
                sessionId,
                "test-sub-1",
                testDestination(contentId, WATCH_SERVICE),
                Role.USER
        );

        SessionInfo chatSessionInfo = getSessionInfo(
                sessionId,
                "test-sub-1",
                testDestination(contentId, CHAT_SERVICE),
                Role.USER
        );

        String viewerTopic = service.topic(viewerSessionInfo);
        String chatTopic = service.topic(chatSessionInfo);

        assertThat(viewerTopic).isEqualTo(
                assertionTopicAnswer(contentId, WATCH_SERVICE)
        );
        assertThat(chatTopic).isEqualTo(
                assertionTopicAnswer(contentId, CHAT_SERVICE)
        );
    }


    @Nested
    @DisplayName("동시 시청 등록 관련 기능 테스트")
    class Watch {

        final String sessionId = "test-session";
        final String contentId = UUID.randomUUID().toString();

        @Test
        @DisplayName("컨텐츠 ID 를 바탕으로 현재 시청중인 동시 시청자 수를 long 타입으로 반환한다.")
        void currentViewerTest(){

            repositoryLogicSet(contentId,23L);

            long viewer = service.getCurrentViewerCount(UUID.fromString(contentId));

            assertThat(viewer).isEqualTo(23L);
        }

        @Nested
        @DisplayName("세션 등록 기능 테스트")
        class Register{

            @Test
            @DisplayName("세션 정보 - 컨텐츠ID 쌍을 기반으로, ViewerSession 을 저장할 수 있다. 이후, 메세지 전송 채널을 구독한다.")
            void registerSessionInfoPerContentTest(){
                SessionInfo info = getSessionInfo(
                        sessionId,
                        "test-sub-1",
                        testDestination(contentId, WATCH_SERVICE),
                        Role.USER
                );

                service.joinViewerSession(info);

                verify(repository,times(1)).register(any(),any(),any());

                // 구독을 진행한다.
                verify(redisTopicRegistry, times(1)).register(
                        eq(assertionTopicAnswer(contentId, WATCH_SERVICE)),
                        eq(sessionId),
                        eq("test-sub-1"),
                        any(RedisMessageListener.class)
                );
            }

            @Test
            @DisplayName("세션의 인가가 없을 시, 세션 등록 시도 없이 인증 예외를 반환한다.")
            void exceptionTestWithNoAuthenticationSession(){
                SessionInfo info = getSessionInfo(
                        sessionId,
                        "test-sub-1",
                        testDestination(contentId, WATCH_SERVICE),
                        null
                );

                assertThatThrownBy(() -> service.joinViewerSession(info))
                        .isInstanceOf(EveryOneException.class); // such as InvalidAuthEvent
                verify(repository,times(0)).register(any(),any(),any());
                verify(redisTopicRegistry, times(0)).register(
                        eq(assertionTopicAnswer(contentId, WATCH_SERVICE)),
                        eq(sessionId),
                        eq("test-sub-1"),
                        any(RedisMessageListener.class)
                );
            }

            @Test
            @DisplayName("사용자가 잠겨있으면, 세션 등록 시도 없이 인증 예외를 반환한다.")
            void exceptionTestWithLockedUser(){
                SessionInfo info = getSessionInfo(
                        sessionId,
                        "test-sub-1",
                        testDestination(contentId, WATCH_SERVICE),
                        Role.USER,
                        true
                );

                assertThatThrownBy(() -> service.joinViewerSession(info))
                        .isInstanceOf(EveryOneException.class); // such as UserLockedException
                verify(repository,times(0)).register(any(),any(),any());
                verify(redisTopicRegistry, times(0)).register(
                        eq(assertionTopicAnswer(contentId, WATCH_SERVICE)),
                        eq(sessionId),
                        eq("test-sub-1"),
                        any(RedisMessageListener.class)
                );
            }

            @Test
            @DisplayName("세션 목적지가 지정된 경로의 형식이 아니면, 세션 등록 시도 없이 예외를 반환한다.")
            void exceptionTestWithNotDefinedDestination(){
                SessionInfo info = getSessionInfo(
                        sessionId,
                        "test-sub-1",
                        testDestination(contentId, WATCH_SERVICE),
                        Role.USER
                );

                assertThatThrownBy(() -> service.joinViewerSession(info))
                        .isInstanceOf(EveryOneException.class); // such as DestinationNotMatchedException
                verify(repository,times(0)).register(any(),any(),any());
                verify(redisTopicRegistry, times(0)).register(
                        eq(assertionTopicAnswer(contentId, WATCH_SERVICE)),
                        eq(sessionId),
                        eq("test-sub-1"),
                        any(RedisMessageListener.class)
                );
            }
        }

        @Nested
        @DisplayName("세션 등록 취소 기능 테스트")
        class Unregister{
            @Test
            @DisplayName("세션 정보 - 컨텐츠ID 쌍을 기반으로, ViewerSession 을 저장할 수 있다.")
            void unregisterSessionInfoPerContentTest(){
                SessionInfo info = getSessionInfo(
                        sessionId,
                        "test-sub-1",
                        testDestination(contentId, WATCH_SERVICE),
                        Role.USER
                );

                service.leaveViewerSession(info);

                verify(repository,times(1)).unregister(any(),any());
                // 구독을 진행한다.
                verify(redisTopicRegistry, times(1)).unregister(
                        eq(sessionId),
                        eq("test-sub-1")
                );
            }

            @Test
            @DisplayName("세션의 인가가 없을 시, 세션 등록 시도 없이 인증 예외를 반환한다.")
            void exceptionTestWithNoAuthenticationSession(){
                SessionInfo info = getSessionInfo(
                        sessionId,
                        "test-sub-1",
                        testDestination(contentId, WATCH_SERVICE),
                        null
                );

                assertThatThrownBy(() -> service.leaveViewerSession(info))
                        .isInstanceOf(EveryOneException.class); // such as InvalidAuthEvent
                verify(repository,times(0)).unregister(any(),any());
                verify(redisTopicRegistry, times(0)).unregister(
                        eq(sessionId),
                        eq("test-sub-1")
                );
            }

            @Test
            @DisplayName("사용자가 잠겨있으면, 세션 등록 시도 없이 인증 예외를 반환한다.")
            void exceptionTestWithLockedUser(){
                SessionInfo info = getSessionInfo(
                        sessionId,
                        "test-sub-1",
                        testDestination(contentId, WATCH_SERVICE),
                        Role.USER,
                        true
                );

                assertThatThrownBy(() -> service.leaveViewerSession(info))
                        .isInstanceOf(EveryOneException.class); // such as UserLockedException
                verify(repository,times(0)).unregister(any(),any());
                verify(redisTopicRegistry, times(0)).unregister(
                        eq(sessionId),
                        eq("test-sub-1")
                );
            }

            @Test
            @DisplayName("세션 목적지가 지정된 경로의 형식이 아니면, 세션 등록 시도 없이 예외를 반환한다.")
            void exceptionTestWithNotDefinedDestination(){
                SessionInfo info = getSessionInfo(
                        sessionId,
                        "test-sub-1",
                        testDestination(contentId, WATCH_SERVICE),
                        Role.USER
                );

                assertThatThrownBy(() -> service.leaveViewerSession(info))
                        .isInstanceOf(EveryOneException.class); // such as DestinationNotMatchedException
                verify(repository,times(0)).unregister(any(),any());
                verify(redisTopicRegistry, times(0)).unregister(
                        eq(sessionId),
                        eq("test-sub-1")
                );
            }
        }
    }
}
