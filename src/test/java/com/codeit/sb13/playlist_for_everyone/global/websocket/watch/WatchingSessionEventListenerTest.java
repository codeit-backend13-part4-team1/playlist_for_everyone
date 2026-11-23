package com.codeit.sb13.playlist_for_everyone.global.websocket.watch;

import com.codeit.sb13.playlist_for_everyone.domain.auth.security.SecurityUser;
import com.codeit.sb13.playlist_for_everyone.global.websocket.SessionInfo;
import com.codeit.sb13.playlist_for_everyone.global.websocket.watching.event.WatchingSessionDisconnectEvent;
import com.codeit.sb13.playlist_for_everyone.global.websocket.watching.event.WatchingSessionEventListener;
import com.codeit.sb13.playlist_for_everyone.global.websocket.watching.event.chat.WatchingChatJoinEvent;
import com.codeit.sb13.playlist_for_everyone.global.websocket.watching.event.chat.WatchingChatLeaveEvent;
import com.codeit.sb13.playlist_for_everyone.global.websocket.watching.event.chat.WatchingChatSendEvent;
import com.codeit.sb13.playlist_for_everyone.global.websocket.watching.event.view.WatchingSessionJoinEvent;
import com.codeit.sb13.playlist_for_everyone.global.websocket.watching.event.view.WatchingSessionLeaveEvent;
import com.codeit.sb13.playlist_for_everyone.global.websocket.watching.event.view.WatchingSessionRefreshEvent;
import com.codeit.sb13.playlist_for_everyone.global.websocket.watching.service.WatchingSessionService;
import com.codeit.sb13.playlist_for_everyone.global.websocket.watching.service.chatting.ChattingSessionService;
import com.codeit.sb13.playlist_for_everyone.global.websocket.watching.service.viewing.ViewingSessionService;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.context.ActiveProfiles;

import java.util.UUID;

import static com.codeit.sb13.playlist_for_everyone.global.websocket.watching.config.WatchingSessionProperties.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * WatchingSession 서비스 이벤트 테스트
 * 채널 인터셉터 로직에서 호출된 이벤트가 실제 서비스로직과 관련 리소스를 호출하는지 확인하는 테스트
 * 구체적으론,
 * - 이벤트에 알맞는 서비스 매서드를 호출하는지
 * - 이벤트에 알맞는 redis 토픽으로 메세지를 발행하는지.
 * 실제 서비스 로직은 서비스 로직 통합 테스트에서 확인한다.
 */

@ExtendWith(MockitoExtension.class)
@DisplayName("WatchingSession 서비스 이벤트 테스트")
@ActiveProfiles("test")
public class WatchingSessionEventListenerTest {

    @Mock
    WatchingSessionService watchingSessionService;

    @Mock
    ChattingSessionService chatService;

    @Mock
    ViewingSessionService viewService;

    @Mock
    SecurityUser auth;

    WatchingSessionEventListener eventListener;

    String sessionId = "test-session";
    String subscriptionId = "test-subscription";

    UUID contentId = UUID.randomUUID();

    String viewDestination =
            PATH_PREFIX + contentId + VIEW_POSTFIX;

    String chatDestination =
            PATH_PREFIX + contentId + CHAT_POSTFIX;

    @BeforeEach
    void setUp() {
        eventListener = new WatchingSessionEventListener(
                watchingSessionService,
                chatService,
                viewService
        );
    }

    /*
     * ==========================================
     * Viewing 이벤트 테스트
     * ==========================================
     */

    @Nested
    @DisplayName("Viewing 이벤트 처리 테스트")
    class ViewingEventTest {

        @Test
        @DisplayName("JOIN 이벤트 수신 시 Viewing JOIN을 호출한다.")
        void watchJoinEventTest() {

            // given
            SessionInfo info = sessionInfoStub(
                    sessionId,
                    subscriptionId,
                    viewDestination,
                    auth
            );

            WatchingSessionJoinEvent event =
                    mock(WatchingSessionJoinEvent.class);

            when(event.info()).thenReturn(info);

            // when
            eventListener.watchJoinEvent(event);

            // then
            verify(watchingSessionService, times(1))
                    .joinViewerSession(
                            sessionId,
                            subscriptionId,
                            viewDestination,
                            auth
                    );

            verifyNoMoreInteractions(watchingSessionService);

            verifyNoInteractions(
                    chatService,
                    viewService
            );
        }

        @Test
        @DisplayName("LEAVE 이벤트 수신 시 Viewing LEAVE를 호출한다.")
        void watchLeaveEventTest() {

            // given
            SessionInfo info = sessionInfoStub(
                    sessionId,
                    subscriptionId,
                    viewDestination,
                    auth
            );

            WatchingSessionLeaveEvent event =
                    mock(WatchingSessionLeaveEvent.class);

            when(event.info()).thenReturn(info);

            // when
            eventListener.watchLeaveEvent(event);

            // then
            verify(watchingSessionService, times(1))
                    .leaveViewerSession(
                            sessionId,
                            subscriptionId,
                            auth
                    );

            verifyNoMoreInteractions(watchingSessionService);

            verifyNoInteractions(
                    chatService,
                    viewService
            );
        }

        @Test
        @DisplayName("REFRESH 이벤트 수신 시 Viewing REFRESH를 호출한다.")
        void watchRefreshEventTest() {

            // given
            SessionInfo info = sessionInfoStub(
                    sessionId,
                    subscriptionId,
                    viewDestination,
                    auth
            );

            WatchingSessionRefreshEvent event =
                    mock(WatchingSessionRefreshEvent.class);

            when(event.info()).thenReturn(info);

            // when
            eventListener.watchRefreshEvent(event);

            // then
            verify(viewService, times(1))
                    .refresh(sessionId);

            verifyNoMoreInteractions(viewService);

            verifyNoInteractions(
                    watchingSessionService,
                    chatService
            );
        }
    }

    /*
     * ==========================================
     * Chatting 이벤트 테스트
     * ==========================================
     */

    @Nested
    @DisplayName("Chatting 이벤트 처리 테스트")
    class ChattingEventTest {

        @Test
        @DisplayName("JOIN 이벤트 수신 시 Chatting JOIN을 호출한다.")
        void chatJoinEventTest() {

            // given
            SessionInfo info = sessionInfoStub(
                    sessionId,
                    subscriptionId,
                    chatDestination,
                    auth
            );

            WatchingChatJoinEvent event =
                    mock(WatchingChatJoinEvent.class);

            when(event.info()).thenReturn(info);

            // when
            eventListener.chatJoinEvent(event);

            // then
            verify(watchingSessionService, times(1))
                    .joinChattingSession(
                            sessionId,
                            subscriptionId,
                            chatDestination,
                            auth
                    );

            verifyNoMoreInteractions(watchingSessionService);

            verifyNoInteractions(
                    chatService,
                    viewService
            );
        }

        @Test
        @DisplayName("LEAVE 이벤트 수신 시 Chatting LEAVE를 호출한다.")
        void chatLeaveEventTest() {

            // given
            SessionInfo info = sessionInfoStub(
                    sessionId,
                    subscriptionId,
                    chatDestination,
                    auth
            );

            WatchingChatLeaveEvent event =
                    mock(WatchingChatLeaveEvent.class);

            when(event.info()).thenReturn(info);

            // when
            eventListener.chatLeaveEvent(event);

            // then
            verify(watchingSessionService, times(1))
                    .leaveChattingSession(
                            sessionId,
                            subscriptionId,
                            auth
                    );

            verifyNoMoreInteractions(watchingSessionService);

            verifyNoInteractions(
                    chatService,
                    viewService
            );
        }
    }

    /*
     * ==========================================
     * Disconnect 이벤트 테스트
     * ==========================================
     */

    @Nested
    @DisplayName("Disconnect 이벤트 처리 테스트")
    class DisconnectEventTest {

        @Test
        @DisplayName("DISCONNECT 이벤트 수신 시 세션 리소스 정리를 호출한다.")
        void disconnectEventTest() {

            // given
            SessionInfo info = sessionInfoStub(
                    sessionId,
                    subscriptionId,
                    viewDestination,
                    auth
            );

            WatchingSessionDisconnectEvent event =
                    mock(WatchingSessionDisconnectEvent.class);

            when(event.info()).thenReturn(info);

            // when
            eventListener.DisconnectEvent(event);

            // then
            verify(watchingSessionService, times(1))
                    .cleanUpWithSessionId(sessionId);

            verifyNoMoreInteractions(watchingSessionService);

            verifyNoInteractions(
                    chatService,
                    viewService
            );
        }
    }


    /*
     * ==========================================
     * 공통 Stub
     * ==========================================
     */

    /**
     * 이벤트에서 참조하는 SessionInfo 객체.
     *
     * 이벤트마다 사용하는 필드가 다르므로
     * 선택적으로 사용되는 필드에 lenient를 적용한다.
     */
    SessionInfo sessionInfoStub(
            String sessionId,
            String subscriptionId,
            String destination,
            SecurityUser auth
    ) {
        SessionInfo info = mock(SessionInfo.class);

        lenient().when(info.session())
                .thenReturn(sessionId);

        lenient().when(info.subscription())
                .thenReturn(subscriptionId);

        lenient().when(info.destination())
                .thenReturn(destination);

        lenient().when(info.user())
                .thenReturn(auth);

        return info;
    }

    /*
     * ==========================================
     * 공통 검증 메서드
     * ==========================================
     */

    /**
     * 입력값 검증 실패 시 하위 서비스가
     * 호출되지 않았음을 검증한다.
     */
    void verifyNoServiceInteractions() {
        verifyNoInteractions(
                watchingSessionService,
                chatService,
                viewService
        );
    }
}