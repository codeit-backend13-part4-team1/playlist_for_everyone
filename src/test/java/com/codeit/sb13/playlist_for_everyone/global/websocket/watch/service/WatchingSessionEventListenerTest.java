package com.codeit.sb13.playlist_for_everyone.global.websocket.watch.service;


import com.codeit.sb13.playlist_for_everyone.domain.auth.security.SecurityUser;
import com.codeit.sb13.playlist_for_everyone.domain.user.entity.Role;
import com.codeit.sb13.playlist_for_everyone.global.exception.EveryOneException;
import com.codeit.sb13.playlist_for_everyone.global.service.redis.RedisMessageDto;
import com.codeit.sb13.playlist_for_everyone.global.websocket.SessionInfo;
import com.codeit.sb13.playlist_for_everyone.global.websocket.watching.WatchingSession;
import com.codeit.sb13.playlist_for_everyone.global.websocket.watching.dto.watcher.ChangeType;
import com.codeit.sb13.playlist_for_everyone.global.websocket.watching.dto.watcher.WatchingSessionChange;
import com.codeit.sb13.playlist_for_everyone.global.websocket.watching.event.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.messaging.Message;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;

import java.util.UUID;

import static com.codeit.sb13.playlist_for_everyone.global.websocket.watching.config.WatchingSessionProperties.MESSAGE_CHAT_PREFIX;
import static com.codeit.sb13.playlist_for_everyone.global.websocket.watching.config.WatchingSessionProperties.MESSAGE_VIEW_PREFIX;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.*;

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
public class WatchingSessionEventListenerTest {

    @Mock
    WatchingSession watchingSession;

    @Mock
    RedisTemplate<String, Object> template;

    @InjectMocks
    WatchingSessionEventListener eventListener;


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

    void setTestingJoinLogic(
            String contentId,
            String serviceType,
            boolean setTopic,
            boolean setWatchingSessionChange
    ){
        String dest = null;
        WatchingSessionChange payloadJoin = null;
        if (setWatchingSessionChange) {
            payloadJoin = new WatchingSessionChange(
                    ChangeType.JOIN,
                    null,
                    0L
            );
        }


        if(setTopic) dest = assertionTopicAnswer(contentId,serviceType);

        if(!setTopic || !setWatchingSessionChange) return;
        // topic 반환 셋.
        given(watchingSession.topic(any(SessionInfo.class)))
                .willReturn(dest);
        // 채널 입장 셋.
        given(watchingSession.joinViewerSession(any(SessionInfo.class)))
                .willReturn(payloadJoin);
    }


    void setTestingLeaveLogic(
            String contentId,
            String serviceType,
            boolean setTopic,
            boolean setWatchingSessionChange
    ){
        String dest = null;
        WatchingSessionChange payloadLeave = null;
        if (setWatchingSessionChange) {
            payloadLeave = new WatchingSessionChange(
                    ChangeType.LEAVE,
                    null,
                    0L
            );
        }

        if(setTopic) dest = assertionTopicAnswer(contentId,serviceType);

        if(!setTopic || !setWatchingSessionChange) return;
        // topic 반환 셋.
        given(watchingSession.topic(any(SessionInfo.class)))
                .willReturn(dest);
        // 채널 퇴장 셋
        given(watchingSession.leaveViewerSession(any(SessionInfo.class)))
                .willReturn(payloadLeave);
    }

    @BeforeEach
    void setUp(){
    }


    @Nested
    @DisplayName("WatchingSession 계열 이벤트 테스트")
    class WatchingSessionEvent{

        @Nested
        @DisplayName("JoinEvent 테스트")
        class Join{

            @Test
            @DisplayName("WatchingSessionJoinEvent 테스트 - 세션정보에 따라 WatchingSessionChange의 type이 JOIN인 메세지를 반환")
            void watchingSessionJoinEventTest() {
                String contentId = UUID.randomUUID().toString();
                String sessionId = "test-session-Id";
                String destination = testDestination(contentId, WATCH_SERVICE);
                SessionInfo info = getSessionInfo(
                        sessionId,
                        "test-sub-1",
                        destination,
                        Role.USER
                );

                setTestingJoinLogic(contentId, WATCH_SERVICE, true, true);

                // 서비스 정보를 바탕으로 메세지를 전송하는 부분 검증.
                given(template.convertAndSend(
                        any(String.class),  // topic
                        any()))             // RedisMessageDto((String) destination, WatchingSessionChange)
                        .willAnswer(inv -> {
                            String topic = inv.getArgument(0);
                            RedisMessageDto<WatchingSessionChange> message = inv.getArgument(1);

                            // 전송 할 토픽은 구독에서 검정한 토픽.
                            assertThat(topic).isEqualTo(assertionTopicAnswer(contentId, WATCH_SERVICE));
                            // 전송은 메세지와 함께 경로를 포함.
                            assertThat(message.destination()).isEqualTo(testDestination(contentId, WATCH_SERVICE));
                            assertThat(message.data().type()).isEqualTo(ChangeType.JOIN);
                            return null;
                        });

                eventListener.watchJoinEvent(new WatchingSessionJoinEvent(info));

            }

            @Test
            @DisplayName("WatcherSession 에서 topic 을 null 로 반환하면, 메세지를 발신하지 않음.")
            void testWithNullTopicReturned(){
                String contentId = UUID.randomUUID().toString();
                String sessionId = "test-session-Id";
                String destination = testDestination(contentId, WATCH_SERVICE);
                SessionInfo info = getSessionInfo(
                        sessionId,
                        "test-sub-1",
                        destination,
                        Role.USER
                );

                setTestingJoinLogic(contentId,WATCH_SERVICE, false, true);

                assertThatThrownBy(() -> eventListener.watchJoinEvent(new WatchingSessionJoinEvent(info)))
                        .isInstanceOf(EveryOneException.class);

                // 저장로직이 가기 전에 로직 종료
                verify(watchingSession, never()).joinViewerSession(any(SessionInfo.class));

            }

            @Test
            @DisplayName("WatcherSession 에서 data 을 null 로 반환하면, 메세지를 발신하지 않음.")
            void testWithNullDataReturned(){
                String contentId = UUID.randomUUID().toString();
                String sessionId = "test-session-Id";
                String destination = testDestination(contentId, WATCH_SERVICE);
                SessionInfo info = getSessionInfo(
                        sessionId,
                        "test-sub-1",
                        destination,
                        Role.USER
                );

                setTestingJoinLogic(contentId,WATCH_SERVICE, true, false);

                // 예외는 이벤트 리스너 단 에서 잡으므로, 예외는 발생하지 않음.
                assertThatThrownBy(() -> eventListener.watchJoinEvent(new WatchingSessionJoinEvent(info)))
                        .isInstanceOf(EveryOneException.class);

                // 호출 로직이 가기 전에 로직 종료
                verify(template, never()).convertAndSend(any(String.class),any(RedisMessageDto.class));

            }
        }

        @Nested
        @DisplayName("LeaveEvent 테스트")
        class Leave {

            @Test
            @DisplayName("WatchingSessionLeaveEvent 테스트 - 세션정보에 따라 WatchingSessionChange의 type이 Leave인 메세지를 반환")
            void watchingSessionLeaveEventTest() {
                String contentId = UUID.randomUUID().toString();
                String sessionId = "test-session-Id";
                String destination = testDestination(contentId, WATCH_SERVICE);
                SessionInfo info = getSessionInfo(
                        sessionId,
                        "test-sub-1",
                        destination,
                        Role.USER
                );

                setTestingLeaveLogic(contentId,WATCH_SERVICE, true, true);

                // 서비스 정보를 바탕으로 메세지를 전송하는 부분 검증.
                given(template.convertAndSend(
                        any(String.class),  // topic
                        any()))             // RedisMessageDto((String) destination, WatchingSessionChange)
                        .willAnswer(inv -> {
                            String topic = inv.getArgument(0);
                            RedisMessageDto<WatchingSessionChange> message = inv.getArgument(1);

                            // 전송 할 토픽은 구독에서 검정한 토픽.
                            assertThat(topic).isEqualTo(assertionTopicAnswer(contentId, WATCH_SERVICE));
                            // 전송은 메세지와 함께 경로를 포함.
                            assertThat(message.destination()).isEqualTo(testDestination(contentId, WATCH_SERVICE));
                            assertThat(message.data().type()).isEqualTo(ChangeType.LEAVE);
                            return null;
                        });

                eventListener.watchLeaveEvent(new WatchingSessionLeaveEvent(info));
            }

            @Test
            @DisplayName("WatcherSession 에서 topic 을 null 로 반환하면, 메세지를 발신하지 않음.")
            void testWithTopicNullReturned(){
                String contentId = UUID.randomUUID().toString();
                String sessionId = "test-session-Id";
                String destination = testDestination(contentId, WATCH_SERVICE);
                SessionInfo info = getSessionInfo(
                        sessionId,
                        "test-sub-1",
                        destination,
                        Role.USER
                );

                setTestingLeaveLogic(contentId,CHAT_SERVICE, false, true);

                eventListener.watchLeaveEvent(new WatchingSessionLeaveEvent(info));

                // 저장로직이 가기 전에 로직 종료
                verify(watchingSession, never()).leaveViewerSession(any(SessionInfo.class));

            }

            @Test
            @DisplayName("WatcherSession 에서 data 을 null 로 반환하면, 메세지를 발신하지 않음.")
            void testWithNullDataReturned(){
                String contentId = UUID.randomUUID().toString();
                String sessionId = "test-session-Id";
                String destination = testDestination(contentId, WATCH_SERVICE);
                SessionInfo info = getSessionInfo(
                        sessionId,
                        "test-sub-1",
                        destination,
                        Role.USER
                );

                setTestingLeaveLogic(contentId,CHAT_SERVICE, true, false);

                // 예외는 이벤트 리스너 단 에서 잡으므로, 예외는 발생하지 않음.
                eventListener.watchLeaveEvent(new WatchingSessionLeaveEvent(info));

                // 호출 로직이 가기 전에 로직 종료
                verify(template, never()).convertAndSend(any(String.class),any(RedisMessageDto.class));

            }

        }

    }

    @Nested
    @DisplayName("ChatSession 계열 이벤트 테스트")
    class ChatSessionEvent {
        String sessionId = "test-session";
        String subscriptionId = "test-subscription";
        String contentId = UUID.randomUUID().toString();
        String destination = testDestination(contentId, CHAT_SERVICE);

        @Nested
        @DisplayName("Join 테스트")
        class Join {

            @Test
            @DisplayName("참여 이벤트가 호출되면, 채팅 세션 참여 매서드를 호출한다.")
            void joinEventQueryTest(){
                SessionInfo info = getSessionInfo(
                        sessionId,
                        subscriptionId,
                        destination,
                        Role.USER
                );

                eventListener.chatJoinEvent(new WatchingChatJoinEvent(info));

                verify(watchingSession,times(1)).joinChatSession(eq(info));
            }

        }

        @Nested
        @DisplayName("Leave 테스트")
        class Leave {

            @Test
            @DisplayName("이탈 이벤트가 호출되면, 채팅 세션 이탈 매서드를 호출한다.")
            void joinEventQueryTest(){
                SessionInfo info = getSessionInfo(
                        sessionId,
                        subscriptionId,
                        destination,
                        Role.USER
                );

                eventListener.chatLeaveEvent(new WatchingChatLeaveEvent(info));

                verify(watchingSession,times(1)).leaveChatSession(eq(info));
            }

        }

        @Nested
        @DisplayName("Send 테스트")
        class Send {

            @Test
            @DisplayName("Send 이벤트가 호출되면, 세션 정보를 바탕으로 채팅 메세지를 브로커에 전송한다.")
            void joinEventQueryTest(){
                String message = "Message";
                SessionInfo info = getSessionInfo(
                        sessionId,
                        subscriptionId,
                        destination,
                        Role.USER
                );

                String topic = assertionTopicAnswer(destination, CHAT_SERVICE);

                RedisMessageDto<String> dto = new RedisMessageDto<>(destination, message);

                given(watchingSession.topic(info)).willReturn(topic);

                eventListener.chatSendEvent(new WatchingChatSendEvent(info, message));

                verify(template,times(1)).convertAndSend(eq(topic),eq(dto));
            }

            @Test
            @DisplayName("경로가 지정된 형식이 아니면 예외 발생. 별도의 리소스를 사용하지 않는다.")
            void exceptTestWithNotValidateDestination(){
                String message = "Message";
                SessionInfo info = getSessionInfo(
                        sessionId,
                        subscriptionId,
                        "/test/path/for/nonvalid/path",
                        Role.USER
                );

                assertThatThrownBy(() -> eventListener.chatSendEvent(new WatchingChatSendEvent(info, message)))
                        .isInstanceOf(EveryOneException.class);

                verifyNoInteractions(template);
            }

        }

    }

}
