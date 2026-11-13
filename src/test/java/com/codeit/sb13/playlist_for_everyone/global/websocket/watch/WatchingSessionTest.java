package com.codeit.sb13.playlist_for_everyone.global.websocket.watch;

import com.codeit.sb13.playlist_for_everyone.domain.auth.security.SecurityUser;
import com.codeit.sb13.playlist_for_everyone.domain.content.dto.response.ContentDto;
import com.codeit.sb13.playlist_for_everyone.domain.content.service.ContentService;
import com.codeit.sb13.playlist_for_everyone.domain.user.dto.response.UserSummary;
import com.codeit.sb13.playlist_for_everyone.domain.user.entity.Role;
import com.codeit.sb13.playlist_for_everyone.domain.user.entity.User;
import com.codeit.sb13.playlist_for_everyone.domain.user.mapper.UserSummaryMapper;
import com.codeit.sb13.playlist_for_everyone.domain.user.service.UserService;
import com.codeit.sb13.playlist_for_everyone.global.exception.EveryOneException;
import com.codeit.sb13.playlist_for_everyone.global.exception.websocket.WebSocketAuthInvalidException;
import com.codeit.sb13.playlist_for_everyone.global.websocket.SessionInfo;
import com.codeit.sb13.playlist_for_everyone.global.websocket.registry.subscription.SubscriptionRegistry;
import com.codeit.sb13.playlist_for_everyone.global.websocket.registry.session.WebSocketRegistry;
import com.codeit.sb13.playlist_for_everyone.global.websocket.registry.session.WebSocketRegistryImpl;
import com.codeit.sb13.playlist_for_everyone.global.websocket.watching.WatchingSession;
import com.codeit.sb13.playlist_for_everyone.global.websocket.watching.WatchingSessionMapper;
import com.codeit.sb13.playlist_for_everyone.global.websocket.watching.dto.chat.ContentChatDto;
import com.codeit.sb13.playlist_for_everyone.global.websocket.watching.dto.watcher.ChangeType;
import com.codeit.sb13.playlist_for_everyone.global.websocket.watching.dto.watcher.WatchingSessionChange;
import com.codeit.sb13.playlist_for_everyone.global.websocket.watching.service.WatchingSessionService;
import org.junit.jupiter.api.*;
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
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;

import static com.codeit.sb13.playlist_for_everyone.global.websocket.watching.config.WatchingSessionProperties.MESSAGE_CHAT_PREFIX;
import static com.codeit.sb13.playlist_for_everyone.global.websocket.watching.config.WatchingSessionProperties.MESSAGE_VIEW_PREFIX;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

/**
 * WatchingSession 서비스 및 User, Content 서비스를 바탕으로 전송 객체를 조립하고,
 * 구독 관리 및 메세지 전송을 담당하는 클래스.
 */

@ExtendWith(MockitoExtension.class)
@DisplayName("WatchingSession 테스트")
public class WatchingSessionTest {

    @Mock
    UserService userService;
    @Mock
    ContentService contentService;
    @Mock
    UserSummaryMapper userSummaryMapper;
    @Mock
    WebSocketRegistry socketRegistry;
    @Mock
    SubscriptionRegistry subscriptionRegistry;
    @Mock
    WebSocketRegistry webSocketRegistry;
    // interface 단은 setup 에서 설정.
    WatchingSessionService watchingSessionService;
    WatchingSessionMapper mapper;
    // 테스트 할 객체
    WatchingSession watchingSession;

    /**
     * 테스트용 SessionInfo 생성 편의 매서드
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


        SecurityUser user = new SecurityUser(
                UUID.randomUUID(),
                "test@email.com",
                "",
                role,
                isLocked
        );
        UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities());

        accessor.setUser(authentication);

        Message<byte[]> message =  MessageBuilder.createMessage(
                new byte[0],
                accessor.getMessageHeaders()
        );
        return new SessionInfo(message);
    }


    /**
     * redis 구독 등록을 위한 테스트용 편의 매서드
     */
    // 테스트용 서비스 타입
    static final String WATCH_SERVICE = "watch";
    static final String CHAT_SERVICE = "chat";

    static String testDestination(String contentId, String serviceType){
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
     * 검증을 위한 stub 정의 매서드
     */

    void watchingSessionLogicSet(UUID contentId, Long count){
        given(watchingSessionService.getCurrentViewerCount(contentId))
                .willReturn(count);
    }

    void userServiceLogicSet(SecurityUser auth){
        userServiceLogicSet(auth,false);
    }

    void userServiceLogicSet(SecurityUser auth, boolean setSummary){
        User user = User.builder()
                .name("김숙희")
                .email(auth.getUsername())
                .role(auth.getRole())
                .password(auth.getPassword())
                .build();
        if (!auth.isEnabled())
            ReflectionTestUtils.setField(user,"enabled",false);
        ReflectionTestUtils.setField(user, "id", auth.getUserId());

        given(userService.getById(any(UUID.class))).willReturn(user);
        if (setSummary)
            given(userSummaryMapper.toSummary(eq(user))).willReturn(new UserSummary(
                    user.getId(),
                    user.getName(),
                    null
        ));
    }

    // todo - ContentService 에서 컨텐츠를 조회하는 매서드가 생기면 활성화.
    void contentServiceLogicSet(){
        given(contentService.getById(any(UUID.class)))
                .willReturn(new ContentDto(
                        null,
                        null,
                        null,
                        null,
                        null,
                        List.of(),
                        0.0,
                        0,
                        0L
                ));
    }


    // 전역적 테스트 공통 인자.
    String contentId = UUID.randomUUID().toString();
    String sessionId = "viewer-session-test";

    @BeforeEach
    void setUp(){
        socketRegistry = new WebSocketRegistryImpl();
        // 별도 모듈 테스트가 있으므로, mock 으로 주입.
        watchingSessionService = mock(WatchingSessionService.class);
        webSocketRegistry = mock(WebSocketRegistryImpl.class);
        mapper = new WatchingSessionMapper();
        watchingSession = new WatchingSession(
                mapper,
                userService,
                contentService,
                watchingSessionService,
                subscriptionRegistry,
                userSummaryMapper,
                webSocketRegistry
        );
    }

    @Nested
    @DisplayName("ViewerSession 테스트")
    class ViewerSessionTest{

        void validateReturnedObject(
                WatchingSessionChange payload,
                long watcherCount,
                SessionInfo info,
                ChangeType type
        ){
            // payload 는 JOIN 타입을 가진다.
            assertThat(payload.type()).isEqualTo(type);
            // 현재 사용 유저 수는 repository 반환값과 동일하다.
            assertThat(payload.watcherCount()).isEqualTo(watcherCount);
            // dto 의 id, 생성 시간은 저장된 세션 데이터와 같다.
//            assertThat(payload.watchingSession().id()).isEqualTo(data.id());
//            assertThat(payload.watchingSession().createdAt()).isEqualTo(data.createdAt());
            // dto 의 watcher 의 userId 는 info 의 user 의 userId 와 같다.
            assertThat(payload.watchingSession().watcher().userId())
                    .isEqualTo(Objects.requireNonNull(info.user()).getUserId());
            // 컨텐츠 id 는 경로에 주입된 컨텐츠 id 와 같다.
            // todo - 콘텐츠 조회 매서드가 생기면 활성.
//            assertThat(payload.watchingSession().content().id())
//                    .isEqualTo(UUID.fromString(contentId));
        }

        @Nested
        @DisplayName("join test")
        class JoinTest{

            // 세션 info 의 인증, 인가, lock 등을 변경하며 테스트 하기 위한 파라매터 생성자.
            static Stream<Arguments> variableRoleAndLockParam(){
                return Stream.of(
                        Arguments.of(Role.USER, true)
//                        Arguments.of("note", false)
                );
            }

            static Stream<Arguments> variableDestinationParam(){
                return Stream.of(
                        Arguments.of(null,null),
                        Arguments.of("/no/accept/destination/in",null)
                );
            }

            @Test
            @DisplayName("Session info 를 받아와서 ViewSession 을 구독 하고, 엔티티 정보를 이용해서 세션 객체를 조립, 반환한다.")
            void joinViewerSessionTest(){
                long watcherCount = 10L;
                SessionInfo info = getSessionInfo(
                        sessionId,
                        "test-sub-1",
                        testDestination(contentId, WATCH_SERVICE),
                        Role.USER
                );

                // 테스트용 세션 데이터 레지스터에 저장.
                given(subscriptionRegistry.get(eq(info)))
                        .willReturn(Optional.of(info));

                // 관련 로직 stub
                userServiceLogicSet(Objects.requireNonNull(info.user()));
                watchingSessionLogicSet(UUID.fromString(contentId), watcherCount);
                contentServiceLogicSet();

                WatchingSessionChange payload = watchingSession.joinViewerSession(info);

                validateReturnedObject(payload, watcherCount, info, ChangeType.JOIN);
            }

            @ParameterizedTest
            @MethodSource("variableRoleAndLockParam")
            @DisplayName("시청 세션 입장 시, 인증이나, 인가가 없다면, 리소스 작업 시도를 하지 않고 예외를 반환한다.")
            void exceptionTestWhenInvalidRole(Role role, boolean isLocked) {
                Long watcherCount = 21L;
                SessionInfo info = getSessionInfo(
                        sessionId,
                        "test-sub-1",
                        testDestination(contentId, WATCH_SERVICE),
                        role,
                        isLocked
                );

                // 테스트용 세션 데이터 레지스터에 저장.
                given(subscriptionRegistry.get(eq(info)))
                        .willReturn(Optional.of(info));

                userServiceLogicSet(info.user());
                assertThatThrownBy(() -> watchingSession.joinViewerSession(info))
                        .isInstanceOf(EveryOneException.class); // InvalidAuthException

                // 실제 세션 저장부 및 구독은 호출되지 않음.
                verify(watchingSessionService, times(0)).joinViewerSession(any());
            }

            @ParameterizedTest
            @MethodSource("variableDestinationParam")
            @DisplayName("시청 세션 입장 시 지정된 형식의 경로가 아니면 리소스 조회를 하지 않고 예외를 반환한다.")
            void exceptionTestWithoutAuthentication(String destination){
                long watcherCount = 43L;
                SessionInfo info = getSessionInfo(
                        sessionId,
                        "test-sub-1",
                        destination,
                        Role.USER
                );

                // 테스트용 세션 데이터 레지스터에 저장.
                given(subscriptionRegistry.get(eq(info)))
                        .willReturn(Optional.of(info));

                assertThatThrownBy(() -> watchingSession.joinViewerSession(info))
                        .isInstanceOf(EveryOneException.class); // InvalidAuthException

                // 실제 세션 저장부는 호출되지 않음.
                verify(watchingSessionService, times(0)).joinViewerSession(any());
            }
        }

        @Nested
        @DisplayName("leave test")
        class LeaveTest{

            String registeredDestination = testDestination(contentId, WATCH_SERVICE);

            // 세션 info 의 인증, 인가, lock 등을 변경하며 테스트 하기 위한 파라매터 생성자.
            static Stream<Arguments> variableRoleAndLockParam(){
                return Stream.of(
                        Arguments.of(Role.USER, true)
//                        Arguments.of(null, false)
                );
            }

            static Stream<Arguments> variableDestinationParam(){
                return Stream.of(
                        Arguments.of(null,null),
                        Arguments.of("/no/accept/destination/in",null)
                );
            }

            @Test
            @DisplayName("Session info 를 받아와서 ViewSession 을 구독 해제 하고, 엔티티 정보를 이용해서 세션 객체를 조립, 반환한다.")
            void leaveViewerSessionTest(){
                long watcherCount = 10L;
                SessionInfo info = getSessionInfo(
                        sessionId,
                        "test-sub-1",
                        registeredDestination,
                        Role.USER
                );

                given(subscriptionRegistry.get(any(SessionInfo.class)))
                        .willReturn(Optional.of(info));

                // 관련 로직 stub
                userServiceLogicSet(Objects.requireNonNull(info.user()));
                watchingSessionLogicSet(UUID.fromString(contentId), watcherCount);
                contentServiceLogicSet();

                WatchingSessionChange payload = watchingSession.leaveViewerSession(info);


                validateReturnedObject(payload, watcherCount, info, ChangeType.LEAVE);
            }

            @Disabled // 구독 취소는 중단되지 않도록 한다.
            @ParameterizedTest
            @MethodSource("variableRoleAndLockParam")
            @DisplayName("시청 세션 입장 시, 인증이나, 인가가 없다면, 리소스 작업 시도를 하지 않고 예외를 반환한다.")
            void exceptionTestWhenInvalidRole(Role role, boolean isLocked) {
                Long watcherCount = 21L;
                SessionInfo info = getSessionInfo(
                        sessionId,
                        "test-sub-1",
                        testDestination(contentId, WATCH_SERVICE),
                        role,
                        isLocked
                );
                given(subscriptionRegistry.get(eq(info)))
                        .willReturn(Optional.of(info));

                userServiceLogicSet(info.user());
                assertThatThrownBy(() -> watchingSession.leaveViewerSession(info))
                        .isInstanceOf(EveryOneException.class); // InvalidAuthException

                // 실제 세션 저장부 및 구독은 호출되지 않음.
                verify(watchingSessionService, times(0)).leaveViewerSession(any());
            }

            @ParameterizedTest
            @MethodSource("variableDestinationParam")
            @DisplayName("시청 세션 입장 시 지정된 형식의 경로가 아니면 리소스 조회를 하지 않고 예외를 반환한다.")
            void exceptionTestWithoutAuthentication(String destination){
                long watcherCount = 43L;
                SessionInfo info = getSessionInfo(
                        sessionId,
                        "test-sub-1",
                        destination,
                        Role.USER
                );
                given(subscriptionRegistry.get(eq(info)))
                        .willReturn(Optional.of(info));

                assertThatThrownBy(() -> watchingSession.leaveViewerSession(info))
                        .isInstanceOf(EveryOneException.class); // InvalidAuthException

                // 실제 세션 저장부는 호출되지 않음.
                verify(watchingSessionService, times(0)).leaveViewerSession(any());
            }

        }
    }


    /**
     * ChatSession 테스트
     * 외부에서 세션 정보를 받아와서, 같은 채팅방(redis 메세지 퍼블리셔)를 구독하는 로직
     * 해당 클래스의 역할은
     * 1. 인증, 인가 검사
     * 2. 경로 검사
     * 3. 이벤트 발신 및 기타 로직 수행
     */
    @Nested
    @DisplayName("ChatSession 테스트")
    class ChatSessionTest {

        static String sessionId = "test-session";
        static String subscriptionId = "test-sub";
        static String contentId = UUID.randomUUID().toString();
        static String destination = testDestination(contentId, CHAT_SERVICE);


        @Nested
        @DisplayName("join 검사")
        class JoinTest {

            @Test
            @DisplayName("세션 정보를 바탕으로, 토픽을 받아오고, ChatSession 을 구독한다.")
            void joinSessionTest(){
                SessionInfo info = getSessionInfo(
                        sessionId,
                        subscriptionId,
                        destination,
                        Role.USER
                );

                // 현재 유저 정보를 통한 인증, 인가 검사용 호출
                userServiceLogicSet(Objects.requireNonNull(info.user()));

                watchingSession.joinChatSession(info);

                verify(watchingSessionService).joinChatSession(eq(info));
            }

            // 경로 정보만 검사함.
            static Stream<Arguments> nullableTestArgv(){
                return Stream.of(
                        Arguments.of(sessionId, subscriptionId, null),
                        Arguments.of(sessionId, subscriptionId, "/test/pattern/123")
                );
            }

            @ParameterizedTest
            @MethodSource("nullableTestArgv")
            @DisplayName("경로 정보가 없거나, 경로 정보가 지정된 형식이 아니면, 리소스를 등록하기 전에 예외를 반환한다.")
            void leaveTestWithNoArgv(String session, String subscription, String destination){
                SessionInfo info = getSessionInfo(
                        session,
                        subscription,
                        destination,
                        Role.USER
                );

                // 리소스 조회는 아예 하지 않는다.
                assertThatThrownBy(() -> watchingSession.joinChatSession(info))
                        .isInstanceOf(EveryOneException.class);

                verifyNoInteractions(watchingSessionService);
                verifyNoInteractions(userService);
            }

            // 우선은 잠김만 검사함.
            static Stream<Arguments> authenticationTestArgv(){
                return Stream.of(
                        Arguments.of(Role.USER,true)
                );
            }

            @ParameterizedTest
            @MethodSource("authenticationTestArgv")
            @DisplayName("유저의 인가 권한이 없거나, 유저가 잠겨있으면 리소스를 등록하기 전에 예외를 반환한다.")
            void leaveTestWithLockedUser(Role role, boolean isLocked){
                SessionInfo info = getSessionInfo(
                        sessionId,
                        subscriptionId,
                        destination,
                        role,
                        isLocked
                );

                userServiceLogicSet(info.user());

                assertThatThrownBy(() -> watchingSession.joinChatSession(info))
                        .isInstanceOf(EveryOneException.class);

                verifyNoInteractions(watchingSessionService);
            }

        }

        @Nested
        @DisplayName("leave 검사")
        class Leave{

            @Test
            @DisplayName("세션 정보를 바탕으로, 토픽을 받아오고, ChatSession 을 구독 취소 한다.")
            void joinSessionTest(){
                SessionInfo info = getSessionInfo(
                        sessionId,
                        subscriptionId,
                        destination,
                        Role.USER
                );

                watchingSession.leaveChatSession(info);

                verify(watchingSessionService).leaveChatSession(eq(info));
            }

            // 경로 정보만 검사함.
            static Stream<Arguments> nullableTestArgv(){
                return Stream.of(
                        Arguments.of(sessionId, subscriptionId, null),
                        Arguments.of(sessionId, subscriptionId, "/test/pattern/123")
                );
            }

            @ParameterizedTest
            @MethodSource("nullableTestArgv")
            @DisplayName("세션 정보나, 경로 정보가 없거나, 경로 정보가 지정된 형식이 아니면, 리소스를 등록하기 전에 예외를 반환한다.")
            void leaveTestWithNoArgv(String session, String subscription, String destination){
                SessionInfo info = getSessionInfo(
                        session,
                        subscription,
                        destination,
                        Role.USER
                );

                // 리소스 조회는 아예 하지 않는다.
                assertThatThrownBy(() -> watchingSession.leaveChatSession(info))
                        .isInstanceOf(EveryOneException.class);

                verifyNoInteractions(watchingSessionService);
                verifyNoInteractions(userService);
            }
        }


        @Nested
        @DisplayName("send 객체 조립 기능 검사.")
        class Send{

            @Test
            @DisplayName("send 로 보낼 메세지 객체를 조립한다.")
            void testGettingChatDto(){
                SessionInfo info = getSessionInfo(
                        sessionId,
                        subscriptionId,
                        destination,
                        Role.USER
                );
                String content = "content";

                userServiceLogicSet(info.user(),true);

                ContentChatDto chat = watchingSession.getContentChatDto(info, content);

                assertThat(chat.content()).isEqualTo(content);
                assertThat(chat.sender()).isNotNull();
            }

            @Test
            @DisplayName("유저가 잠긴 유저라면, 예외를 던진다.")
            void exceptionTestWithLockedUser(){
                SessionInfo info = getSessionInfo(
                        sessionId,
                        subscriptionId,
                        destination,
                        Role.USER,
                        true
                );
                String content = "content";

                userServiceLogicSet(info.user());

                assertThatThrownBy(() -> watchingSession.getContentChatDto(info, content))
                        .isInstanceOf(WebSocketAuthInvalidException.class);
            }

        }

    }


}
