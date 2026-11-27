package com.codeit.sb13.playlist_for_everyone.global.websocket.watch.service;

import com.codeit.sb13.playlist_for_everyone.domain.content.service.ContentService;
import com.codeit.sb13.playlist_for_everyone.domain.user.service.UserService;
import com.codeit.sb13.playlist_for_everyone.global.service.lock.SpreadLock;
import com.codeit.sb13.playlist_for_everyone.global.websocket.entity.WSSession;
import com.codeit.sb13.playlist_for_everyone.global.websocket.entity.WSSubscription;
import com.codeit.sb13.playlist_for_everyone.global.websocket.registry.session.WebSocketSessionRegistry;
import com.codeit.sb13.playlist_for_everyone.global.websocket.watching.WatchingSessionMapper;
import com.codeit.sb13.playlist_for_everyone.global.websocket.watching.service.WatchingSessionService;
import com.codeit.sb13.playlist_for_everyone.global.websocket.watching.service.WatchingSessionServiceImpl;
import com.codeit.sb13.playlist_for_everyone.global.websocket.watching.service.chatting.ChattingSessionService;
import com.codeit.sb13.playlist_for_everyone.global.websocket.watching.service.viewing.ViewingSessionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.redis.core.RedisTemplate;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static com.codeit.sb13.playlist_for_everyone.global.websocket.watching.config.WatchingSessionProperties.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("WatchingSessionService 기본 동작 테스트")
public class WatchingSessionBasicTest {
    @Mock
    WebSocketSessionRegistry sessionRegistry;

    @Mock
    UserService userService;

    @Mock
    ContentService contentService;

    @Mock
    ViewingSessionService viewingSessionService;

    @Mock
    ChattingSessionService chattingSessionService;

    @Mock
    ApplicationEventPublisher eventPublisher;

    @Mock
    WatchingSessionMapper mapper;

    @Mock
    RedisTemplate<String, Object> redisTemplate;

    WatchingSessionService watchingSessionService;

    @BeforeEach
    void setUp() {
        setUpParam();
    }

    /**
     * 의존성 주입.
     */
    void setUpParam() {
        watchingSessionService = new WatchingSessionServiceImpl(
                sessionRegistry,
                viewingSessionService,
                chattingSessionService,
                userService,
                contentService,
                mapper,
                eventPublisher,
                new SpreadLock()
        );
    }

    @Nested
    @DisplayName("cleanUp 작업 테스트")
    class CleanUpTest {

        String sessionId = "test-session";
        WSSession session;
        UUID contentId = UUID.randomUUID();

        List<WSSubscription> viewContainer;
        List<WSSubscription> chatContainer;
        List<WSSubscription> sessionContainer;

        @Nested
        @DisplayName("contentId 관련 시나리오")
        class ContentId {

            @BeforeEach
            void setup() throws Exception {
                session = session(sessionId);
                viewContainer = new ArrayList<>();
                chatContainer = new ArrayList<>();

                sessionStub(sessionId, session);

                viewContainer.add(subscription(sessionId, "sub-1", viewDestination(contentId)));
                viewContainer.add(subscription(sessionId, "sub-2", viewDestination(contentId)));
                viewContainer.add(subscription(sessionId, "sub-3", viewDestination(contentId)));
                viewContainer.add(subscription(sessionId, "sub-4", viewDestination(contentId)));
                chatContainer.add(subscription(sessionId, "sub-5", chatDestination(contentId)));
                chatContainer.add(subscription(sessionId, "sub-6", chatDestination(contentId)));
                chatContainer.add(subscription(sessionId, "sub-7", chatDestination(contentId)));
                chatContainer.add(subscription(sessionId, "sub-8", chatDestination(contentId)));
            }

            @Test
            @DisplayName("content id 가 null 이면, NPE 반환")
            void cleanupWithContentIdNPETest() throws Exception {
                assertThatThrownBy(() -> watchingSessionService.cleanUpWithContentId(null))
                        .isInstanceOf(NullPointerException.class);
            }

            @Test
            @DisplayName("비즈니스 로직에서 구독 해제 후 반환한 구독 객체를 전부 unsubscribe 한다.")
            void cleanupWithContentIdTest() throws Exception {
                destroyStub(contentId,viewContainer,chatContainer);

                List<WSSubscription> expected = new ArrayList<>();
                expected.addAll(viewContainer);
                expected.addAll(chatContainer);

                ArgumentCaptor<WSSubscription> captor = ArgumentCaptor.forClass(WSSubscription.class);

                watchingSessionService.cleanUpWithContentId(contentId);

                // 함수 검증
                verify(viewingSessionService).destroy(contentId);
                verify(chattingSessionService).destroy(contentId);

                verify(session, times(expected.size()))
                        .unsubscribe(captor.capture());

                List<WSSubscription> actual = captor.getAllValues();

                assertThat(actual)
                        .containsExactlyInAnyOrderElementsOf(expected);

            }

        }

        @Nested
        @DisplayName("sessionId 관련 시나리오")
        class SessionId {

            void addToViewContainer(WSSubscription subscription) {
                sessionContainer.add(subscription);
                viewContainer.add(subscription);
            }

            void addToChatContainer(WSSubscription subscription) {
                sessionContainer.add(subscription);
                chatContainer.add(subscription);
            }

            @BeforeEach
            void setup() throws Exception{
                sessionContainer = new ArrayList<>();
                viewContainer = new ArrayList<>();
                chatContainer = new ArrayList<>();
                session = session(sessionId);

                // 세션 내부에 미리 구독 추가
                addToViewContainer(subscription(sessionId, "sub-1", viewDestination(contentId)));
                addToViewContainer(subscription(sessionId, "sub-2", viewDestination(contentId)));
                addToViewContainer(subscription(sessionId, "sub-3", viewDestination(contentId)));
                addToViewContainer(subscription(sessionId, "sub-4", viewDestination(contentId)));
                addToChatContainer(subscription(sessionId, "sub-5", chatDestination(contentId)));
                addToChatContainer(subscription(sessionId, "sub-6", chatDestination(contentId)));
                addToChatContainer(subscription(sessionId, "sub-7", chatDestination(contentId)));
                addToChatContainer(subscription(sessionId, "sub-8", chatDestination(contentId)));

            }

            @Test
            @DisplayName("null 을 인자로 보내면 NPE 가 발생한다.")
            void cleanupWithSessionIdNPETest(){
                assertThatThrownBy(() -> watchingSessionService.cleanUpWithSessionId(null))
                        .isInstanceOf(NullPointerException.class);
            }

            @Test
            @DisplayName("세션에 존재하는 모든 구독을 순회하며 비즈니스 리소스를 해지한다.")
            void cleanupWithSessionIdTest() throws Exception {
                sessionStub(sessionId, session);
                // 세션이 담고있는(sessionContainer) 모든 구독 반환.
                lenient().when(session.getAllSubscriptions()).thenReturn(sessionContainer);

                leaveAllStub(contentId, chatContainer, viewContainer);

                watchingSessionService.cleanUpWithSessionId(sessionId);

            }

        }

    }

    /*
     * 비즈니스 로직 stub
     */

    void destroyStub(UUID contentId, List<WSSubscription> viewReturn, List<WSSubscription> chatReturn){
        when(viewingSessionService.destroy(contentId)).thenReturn(viewReturn);
        when(chattingSessionService.destroy(contentId)).thenReturn(chatReturn);
    }

    void leaveAllStub(UUID contentId, List<WSSubscription> chatContainer, List<WSSubscription> viewContainer){
        doAnswer(invocation -> {
            List<WSSubscription> subscriptions = invocation.getArgument(0);
            UUID id = invocation.getArgument(1);

            assertThat(subscriptions)
                    .containsExactlyInAnyOrderElementsOf(chatContainer);

            assertThat(id).isEqualTo(contentId);

            return null;

        }).when(chattingSessionService).leaveAll(
                anyList(),
                eq(contentId)
        );

        doAnswer(invocation -> {
            List<WSSubscription> subscriptions = invocation.getArgument(0);
            UUID id = invocation.getArgument(1);

            assertThat(subscriptions)
                    .containsExactlyInAnyOrderElementsOf(viewContainer);

            assertThat(id).isEqualTo(contentId);

            return null;

        }).when(viewingSessionService).leaveAll(
                anyList(),
                eq(contentId)
        );
    }

    /*
     * 공통 세션 stub
     */

    void sessionStub(String sessionId, WSSession session){
        lenient().when(sessionRegistry.query(sessionId)).thenReturn(Optional.of(session));
    }

    /*
     * 객체 반환 매서드
     */
    WSSession session(String sessionId) throws IOException {
        WSSession s = mock(WSSession.class);
        lenient().when(s.id()).thenReturn(sessionId);
        lenient().doNothing().when(s).disconnect();
        return s;
    }

    WSSubscription subscription(String sessionId, String subscriptionId, String destination) {
        return new WSSubscription(sessionId, subscriptionId, destination);
    }

    /*
     * 유틸리티 편의 매서드
     */
    String viewDestination(UUID contentId){
        return PATH_PREFIX + contentId + VIEW_POSTFIX;
    }

    String chatDestination(UUID contentId){
        return PATH_PREFIX + contentId + CHAT_POSTFIX;
    }

}