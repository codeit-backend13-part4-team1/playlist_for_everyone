package com.codeit.sb13.playlist_for_everyone.global.websocket.watch;

import com.codeit.sb13.playlist_for_everyone.domain.auth.security.SecurityUser;
import com.codeit.sb13.playlist_for_everyone.domain.content.entity.Content;
import com.codeit.sb13.playlist_for_everyone.domain.content.service.ContentService;
import com.codeit.sb13.playlist_for_everyone.domain.user.entity.Role;
import com.codeit.sb13.playlist_for_everyone.domain.user.entity.User;
import com.codeit.sb13.playlist_for_everyone.domain.user.service.UserService;
import com.codeit.sb13.playlist_for_everyone.global.exception.EveryOneException;
import com.codeit.sb13.playlist_for_everyone.global.exception.websocket.WebSocketDestinationInvalidException;
import com.codeit.sb13.playlist_for_everyone.global.websocket.SessionData;
import com.codeit.sb13.playlist_for_everyone.global.websocket.SessionInfo;
import com.codeit.sb13.playlist_for_everyone.global.websocket.registry.ConnectionRegistry;
import com.codeit.sb13.playlist_for_everyone.global.websocket.registry.InMemoryConnectionRegistry;
import com.codeit.sb13.playlist_for_everyone.global.websocket.watching.WatchingSession;
import com.codeit.sb13.playlist_for_everyone.global.websocket.watching.WatchingSessionMapper;
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

import java.util.Objects;
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

    // interface 단은 setup 에서 설정.
    WatchingSessionService watchingSessionService;
    ConnectionRegistry connectionRegistry;
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
     * 검증을 위한 stub 정의 매서드
     */

    void watchingSessionLogicSet(UUID contentId, Long count){
        given(watchingSessionService.getCurrentViewerCount(contentId))
                .willReturn(count);
    }

    void userServiceLogicSet(SecurityUser auth){
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
    }

    // todo - ContentService 에서 컨텐츠를 조회하는 매서드가 생기면 활성화.
    void contentServiceLogicSet(Content content){
//        given(contentService.getById(any(UUID.class))).willReturn(content);
    }


    // 전역적 테스트 공통 인자.
    String contentId = UUID.randomUUID().toString();
    String sessionId = "viewer-session-test";

    @BeforeEach
    void setUp(){
        connectionRegistry = new InMemoryConnectionRegistry();
        // 별도 모듈 테스트가 있으므로, mock 으로 주입.
        watchingSessionService = mock(WatchingSessionService.class);
        mapper = new WatchingSessionMapper();
        watchingSession = new WatchingSession(
                mapper,
                userService,
                contentService,
                watchingSessionService,
                connectionRegistry
        );

        // 커넥션 세션 저장
        SessionInfo info = getSessionInfo(
                sessionId,
                null,
                null,
                Role.USER
        );

        SessionData data = info.toData();
        connectionRegistry.register(data);

    }

    @Nested
    @DisplayName("ViewerSession 테스트")
    class ViewerSessionTest{

        void validateReturnedObject(
                WatchingSessionChange payload,
                long watcherCount,
                SessionInfo info,
                SessionData data,
                ChangeType type
        ){
            // payload 는 JOIN 타입을 가진다.
            assertThat(payload.type()).isEqualTo(type);
            // 현재 사용 유저 수는 repository 반환값과 동일하다.
            assertThat(payload.watcherCount()).isEqualTo(watcherCount);
            // dto 의 id, 생성 시간은 저장된 세션 데이터와 같다.
            assertThat(payload.watchingSession().id()).isEqualTo(data.id());
            assertThat(payload.watchingSession().createdAt()).isEqualTo(data.createdAt());
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
                SessionData data = info.toData();
                connectionRegistry.register(data);

                // 관련 로직 stub
                userServiceLogicSet(Objects.requireNonNull(info.user()));
                watchingSessionLogicSet(UUID.fromString(contentId), watcherCount);

                WatchingSessionChange payload = watchingSession.joinViewerSession(info);

                validateReturnedObject(payload, watcherCount, info, data,ChangeType.JOIN);
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
                SessionData data = info.toData();
                connectionRegistry.register(data);

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
                SessionData data = info.toData();
                connectionRegistry.register(data);

                assertThatThrownBy(() -> watchingSession.joinViewerSession(info))
                        .isInstanceOf(WebSocketDestinationInvalidException.class); // InvalidAuthException

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
            void joinViewerSessionTest(){
                long watcherCount = 10L;
                SessionInfo info = getSessionInfo(
                        sessionId,
                        "test-sub-1",
                        null,
                        Role.USER
                );

                // 세션 레지스터에 등록 시뮬.
                SessionData data = SessionData.builder()
                        .session(info.session())
                        .subscription(info.subscription())
                        .destination(registeredDestination) // 구독 시 저장되었던 경로
                        .principal(info.principal())
                        .build();
                connectionRegistry.register(data);

                // 관련 로직 stub
                userServiceLogicSet(Objects.requireNonNull(info.user()));
                watchingSessionLogicSet(UUID.fromString(contentId), watcherCount);

                WatchingSessionChange payload = watchingSession.leaveViewerSession(info);


                validateReturnedObject(payload, watcherCount, info, data,ChangeType.LEAVE);
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

                // 세션 레지스터에 등록 시뮬.
                SessionData data = SessionData.builder()
                        .session(info.session())
                        .subscription(info.subscription())
                        .destination(registeredDestination) // 구독 시 저장되었던 경로
                        .principal(info.principal())
                        .build();
                connectionRegistry.register(data);

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

                // 세션 레지스터에 등록 시뮬.
                SessionData data = SessionData.builder()
                        .session(info.session())
                        .subscription(info.subscription())
                        .destination(destination) // 구독 시 저장되었던 경로
                        .principal(info.principal())
                        .build();
                connectionRegistry.register(data);

                assertThatThrownBy(() -> watchingSession.leaveViewerSession(info))
                        .isInstanceOf(WebSocketDestinationInvalidException.class); // InvalidAuthException

                // 실제 세션 저장부는 호출되지 않음.
                verify(watchingSessionService, times(0)).leaveViewerSession(any());
            }

        }
    }




}
