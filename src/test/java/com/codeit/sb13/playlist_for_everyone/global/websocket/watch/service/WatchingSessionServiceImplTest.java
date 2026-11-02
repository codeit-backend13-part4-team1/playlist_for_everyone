package com.codeit.sb13.playlist_for_everyone.global.websocket.watch.service;


import com.codeit.sb13.playlist_for_everyone.domain.auth.security.SecurityUser;
import com.codeit.sb13.playlist_for_everyone.domain.content.service.ContentService;
import com.codeit.sb13.playlist_for_everyone.domain.user.entity.Role;
import com.codeit.sb13.playlist_for_everyone.domain.user.entity.User;
import com.codeit.sb13.playlist_for_everyone.domain.user.service.UserService;
import com.codeit.sb13.playlist_for_everyone.global.service.RedisMessageDto;
import com.codeit.sb13.playlist_for_everyone.global.service.RedisTopicRegistry;
import com.codeit.sb13.playlist_for_everyone.global.websocket.registry.ConnectionRegistry;
import com.codeit.sb13.playlist_for_everyone.global.websocket.SessionData;
import com.codeit.sb13.playlist_for_everyone.global.websocket.service.WatcherRegistry;
import com.codeit.sb13.playlist_for_everyone.global.websocket.watching.service.WatchingSessionServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;

import java.util.UUID;

import static com.codeit.sb13.playlist_for_everyone.global.websocket.watching.config.WatchingSessionProperties.*;
import static org.mockito.BDDMockito.*;

/*
 * 세션 정보를 받아와서 동시 시청 도메인 관련 작업을 하는 클래스
 * 타 도메인 서비스는 Mock 객체로 대체한다.
 * 각 매서드 에서 데이터의 흐름과, 매서드 호출을 검사한다.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("세션 정보를 받아와서 동시 시청 도메인 관련 작업을 하는 서비스 테스트")
public class WatchingSessionServiceImplTest {

    @Mock
    UserService userService;

    @Mock
    ContentService contentService;

    @Mock
    WatcherRegistry watcherRegistry;

    @Mock
    RedisTopicRegistry redisTopicRegistry;

    @Mock
    ConnectionRegistry connectionRegistry;

    @Mock
    RedisTemplate<String, Object> redisTemplate;

    WatchingSessionServiceImpl watchingSessionServiceImpl;

    @BeforeEach
    void setUp(){
        watchingSessionServiceImpl =
                new WatchingSessionServiceImpl(
                        connectionRegistry,
                        watcherRegistry,
                        redisTopicRegistry,
                        userService,
                        contentService,
                        redisTemplate
                );
    }

    void getUserDataFromSessionDataTest(String sessionId, String subscriptionId, UUID userId){
        // 세션 등록 정보로 부터 제이터 가져옴
        SecurityUser principal = new SecurityUser(userId,"test@email.com","", Role.USER,false);

        given(connectionRegistry.get(eq(sessionId),eq(subscriptionId)))
                .willReturn(SessionData.builder()
                                .session(sessionId)
                                .subscription(subscriptionId)
                                .principal(new UsernamePasswordAuthenticationToken(
                                        principal,
                                        null,
                                        principal.getAuthorities()))
                                .build()
                );
    }

    void checkRedisSessionTopicRegistered(String sessionId, String subscriptionId, String contentId){
        verify(redisTopicRegistry,times(1))
                .register(
                        eq(watchTopic(contentId)),
                        eq(sessionId),
                        eq(subscriptionId)
                );
    }

    void checkRedisChatTopicRegistered(String sessionId, String subscriptionId, String contentId){
        verify(redisTopicRegistry,times(1))
                .register(
                        eq(chatTopic(contentId)),
                        eq(sessionId),
                        eq(subscriptionId)
                );
    }

    void checkRedisTopicUnregistered(String sessionId, String subscriptionId){
        verify(redisTopicRegistry,times(1))
                .unregister(
                        eq(sessionId),
                        eq(subscriptionId)
                );
    }



    String watchTopic(String contentId){
        return MESSAGE_VIEW_PREFIX + contentId;
    }

    String chatTopic(String contentId){
        return MESSAGE_CHAT_PREFIX + contentId;
    }

    @Nested
    @DisplayName("동시 시청 등록 관련 기능 테스트")
    class WatchingSession{



        @Test
        @DisplayName("채널을 구독하면 채널을 redis 에 등록하고, redis message 를 구독한다. 이후, 채널 구독 이벤트를 발생 시킨다.")
        void subscriptionTest(){
            String sessionId = "test-session";
            String subscriptionId = "test-sub-1";
            String contentId = UUID.randomUUID().toString();

            UUID userId = UUID.randomUUID();

            getUserDataFromSessionDataTest(sessionId,subscriptionId,userId);

            // watchingSessionChange 를 생성하기 위한 로직 등록
            // 유저 조회 given
            given(userService.getById(userId))
                    .willReturn(User.builder().email("test@email.com").role(Role.USER).build());
            // 컨텐츠 조회 given
            // todo - 아직 컨텐츠 조회가 생성되지 않았음으로, 생성되면 추가.


            watchingSessionServiceImpl.subscribeWatchingSession(sessionId,subscriptionId,contentId);


            // redis 에 watch 세션을 등록한다.
            verify(watcherRegistry,times(1))
                    .register(eq(sessionId),eq(subscriptionId),eq(contentId));
            // join - leave 메세지를 보내기(받기) 위해 채널을 구독한다.
            checkRedisSessionTopicRegistered(sessionId,subscriptionId,contentId);

            // JOIN 메세지 발행.
            verify(redisTemplate,times(1))
                    .convertAndSend(
                            eq(watchTopic(contentId)),
                            any(RedisMessageDto.class)
                    );


        }

        @Test
        @DisplayName("채널 구독 취소를 하면 redis 등록을 취소하고, redis message 에서 등록을 취소한다. 이후, 채널 체인지 이벤트를 발행한다.")
        void unsubscriptionTest(){
            String sessionId = "test-session";
            String subscriptionId = "test-sub-1";
            String contentId = UUID.randomUUID().toString();

            UUID userId = UUID.randomUUID();

            getUserDataFromSessionDataTest(sessionId,subscriptionId,userId);

            // watchingSessionChange 를 생성하기 위한 로직 등록
            // 유저 조회 given
            given(userService.getById(userId))
                    .willReturn(User.builder().email("test@email.com").role(Role.USER).build());
            // 컨텐츠 조회 given
            given(watcherRegistry.getContent(sessionId, subscriptionId))
                    .willReturn(contentId);
            // todo - 아직 컨텐츠 조회가 생성되지 않았음으로, 생성되면 추가.

            watchingSessionServiceImpl.unsubscribeWatchingSession(sessionId,subscriptionId);

            // redis 에 watch 세션을 등록 해제 한다.
            verify(watcherRegistry,times(1))
                    .unregister(eq(sessionId),eq(subscriptionId));

            // join - leave 메세지를 보내기(받기) 위한 채널을 구독 해제한다.
            checkRedisTopicUnregistered(sessionId,subscriptionId);

            // Leave 메세지 발행
            verify(redisTemplate,times(1))
                    .convertAndSend(
                            eq(watchTopic(contentId)),
                            any(RedisMessageDto.class)
                    );
        }

    }

    @Nested
    @DisplayName("동시 시청 채팅 관련 테스트")
    class ChattingTest{

        @Test
        @DisplayName("채팅 서비스 등록 시, sessionRegistry 에서 데이터를 받아와서 redis message 를 구독한다.")
        void subscribeChat(){
            String sessionId = "test-session";
            String subscriptionId = "test-sub-1";
            String contentId = UUID.randomUUID().toString();

            UUID userId = UUID.randomUUID();

            // 세션 저장소 로 부터 유저 정보 받아옴
            getUserDataFromSessionDataTest(sessionId,subscriptionId,userId);

            watchingSessionServiceImpl.subscribeChatSession(sessionId, subscriptionId, contentId);

            // redis message 구독
            checkRedisChatTopicRegistered(sessionId, subscriptionId, contentId);
        }

        @Test
        @DisplayName("채팅 서비스 등록 취소 시, sessionRegistry 에서 데이터를 받아와서 redis message 를 구독 취소한다.")
        void unsubscribeChat(){
            String sessionId = "test-session";
            String subscriptionId = "test-sub-1";
            String contentId = UUID.randomUUID().toString();

            UUID userId = UUID.randomUUID();

            // 세션 저장소 로 부터 유저 정보 받아옴
            getUserDataFromSessionDataTest(sessionId,subscriptionId,userId);

            watchingSessionServiceImpl.unsubscribeChatSession(sessionId, subscriptionId);

            // redis message 구독
            checkRedisTopicUnregistered(sessionId, subscriptionId);
        }
    }

}
