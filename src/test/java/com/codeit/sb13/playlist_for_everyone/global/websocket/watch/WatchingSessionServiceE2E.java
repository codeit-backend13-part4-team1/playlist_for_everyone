package com.codeit.sb13.playlist_for_everyone.global.websocket.watch;


import com.codeit.sb13.playlist_for_everyone.domain.auth.jwt.JwtTokenProvider;
import com.codeit.sb13.playlist_for_everyone.domain.auth.repository.SessionStartResult;
import com.codeit.sb13.playlist_for_everyone.domain.auth.repository.UserSessionRepository;
import com.codeit.sb13.playlist_for_everyone.domain.auth.service.AdminInitializationService;
import com.codeit.sb13.playlist_for_everyone.domain.content.dto.response.ContentDto;
import com.codeit.sb13.playlist_for_everyone.domain.content.entity.ContentType;
import com.codeit.sb13.playlist_for_everyone.domain.content.service.ContentService;
import com.codeit.sb13.playlist_for_everyone.domain.user.entity.User;
import com.codeit.sb13.playlist_for_everyone.domain.user.repository.UserRepository;
import com.codeit.sb13.playlist_for_everyone.global.websocket.event.WebSocketEventFactory;
import com.codeit.sb13.playlist_for_everyone.global.websocket.registry.subscription.SubscriptionRegistry;
import com.codeit.sb13.playlist_for_everyone.global.websocket.watching.dto.chat.ContentChatSendRequest;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.messaging.converter.JacksonJsonMessageConverter;
import org.springframework.messaging.simp.stomp.StompFrameHandler;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.lang.reflect.Type;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.BDDMockito.*;

@Testcontainers
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
            "everyone.websocket.event.multiple=true"
        }
)
@ActiveProfiles("test")
@DirtiesContext(
        classMode = DirtiesContext.ClassMode.AFTER_CLASS
)
@Slf4j
public class WatchingSessionServiceE2E {

    @LocalServerPort int port;

    @Autowired
    SubscriptionRegistry subscriptionRegistry;

    WebSocketSession clientSession;

    BlockingQueue<String> response;

    @Container
    static GenericContainer<?> redis =
            new GenericContainer<>(
                    DockerImageName.parse("redis:7.4-alpine")
            ).withExposedPorts(6379);

    @DynamicPropertySource
    static void redisProperties(
            DynamicPropertyRegistry registry
    ) {
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add(
                "spring.data.redis.port",
                redis::getFirstMappedPort
        );
        registry.add(
                "spring.data.redis.ssl.enabled",
                () -> false
        );
    }

    // jwt 생성용 의돈성
    @Autowired
    UserRepository userRepository;

    @Autowired
    AdminInitializationService adminInitializationService;

    @Autowired
    JwtTokenProvider jwtTokenProvider;
    @Autowired
    UserSessionRepository userSessionRepository;

    String accessToken;

    @MockitoBean
    ContentService contentService;

    @Autowired
    WebSocketEventFactory webSocketEventFactory;



    @BeforeEach
    void setup(){
        // 초기 Admin 계정 설정.
        adminInitializationService.initialize("admin@admin.com", "admin", "password");

        Optional<User> admin = userRepository.findByEmail("admin@admin.com");

        admin.ifPresent(user -> {
            SessionStartResult session = userSessionRepository.start(user.getId(), Duration.ofMinutes(1));
            accessToken = jwtTokenProvider.createAccessToken(user.getId(),session.sessionId());
        });

        given(contentService.getById(any(UUID.class)))
                .willReturn(new ContentDto(
                        UUID.randomUUID(),
                        ContentType.sport,
                        "description",
                        "thumbnail",
                        "url",
                        List.of(),
                        0.0,
                        0,
                        1L
                ));
    }


    StompFrameHandler getStompHandler(CompletableFuture<String> received){
        return new StompFrameHandler() {

            @Override
            public Type getPayloadType(StompHeaders headers) {
                return byte[].class;
            }

            @Override
            public void handleFrame(
                    StompHeaders headers,
                    Object payload
            ) {
                String json = new String(
                        (byte[]) payload,
                        StandardCharsets.UTF_8
                );

                log.info("STOMP 수신 JSON: {}", json);
                received.complete(json);
            }
        };
    }

    StompFrameHandler getStompHandler(BlockingQueue<String> received){
        return new StompFrameHandler() {

            @Override
            public Type getPayloadType(StompHeaders headers) {
                return byte[].class;
            }

            @Override
            public void handleFrame(
                    StompHeaders headers,
                    Object payload
            ) {
                String json = new String(
                        (byte[]) payload,
                        StandardCharsets.UTF_8
                );

                log.info("STOMP 수신 JSON: {}", json);
                received.offer(json);
            }
        };
    }


    /*
     * 세션 관련 리소스 반환 매서드
     */

    CompletableFuture<StompSession> getSession(WebSocketStompClient client){
        client.setMessageConverter(new JacksonJsonMessageConverter());

        return client.connectAsync(
                URI.create("ws://localhost:" + port + "/ws"),
                getHandShakeHeader(accessToken),
                getStompHeader(accessToken),
                new StompSessionHandlerAdapter() {}
        );
    }

    StompHeaders getStompHeader(String token){
        StompHeaders header = new StompHeaders();
        header.add(
                "Authorization",
                "Bearer " + token
        );
        return header;
    }

    WebSocketHttpHeaders getHandShakeHeader(String token){
        WebSocketHttpHeaders header = new WebSocketHttpHeaders();
        header.setBearerAuth(token);
        return header;
    }

    /*
     * 리소스 세팅 관련 경로 매서드
     */
    String viewingSessionDestination(String id){
        return "/sub/contents/" + id + "/watch";
    }

    String chattingSessionSubscriptionDst(String id){
        return "/sub/contents/" + id + "/chat";
    }

    String chattingSessionSendDst(String id){
        return "/pub/contents/" + id + "/chat";
    }

    /**
     * 1. 연결 성공 여부
     * 2. 구독 시 초기 데이터 반환 여부
     * 3. 메세지 전송 시 서버가 처리 여부
     * 4. 메세지 해지 여부
     */

    @Test
    @DisplayName("세션 연결 시 세션 등록이 성혹하는 지 확인.")
    void connectTest() throws Exception {
        WebSocketStompClient client = new WebSocketStompClient(new StandardWebSocketClient());
        // 테스트 용 리소스
        StompSession session1 = getSession(client).get(5, TimeUnit.SECONDS);
        StompSession session2 = getSession(client).get(5, TimeUnit.SECONDS);

        try {
            BlockingQueue<String> received1 = new LinkedBlockingQueue<>();
            BlockingQueue<String> received2 = new LinkedBlockingQueue<>();

            UUID contentId = UUID.randomUUID();

            // 6. 채팅방 구독
            session1.subscribe(
                    viewingSessionDestination(contentId.toString()),
                    getStompHandler(received1)
            );

            StompSession.Subscription sessionSub2 = session2.subscribe(
                    viewingSessionDestination(contentId.toString()),
                    getStompHandler(received2)
            );


            // 구독 이후 서버 응답을 최대 5초간 대기
            String joinReceive1 = received1.poll(5, TimeUnit.SECONDS);
            String joinReceive2 = received1.poll(5, TimeUnit.SECONDS);

            assertNotNull(
                    joinReceive1,
                    "JOIN 메시지가 도착하지 않았습니다."
            );
            assertNotNull(
                    joinReceive2,
                    "JOIN 메시지가 도착하지 않았습니다."
            );

            sessionSub2.unsubscribe();
            String leaveReceive2 = received1.poll(10, TimeUnit.SECONDS);

            assertNotNull(
                    leaveReceive2,
                    "UNSUBSCRIBE 이후 LEAVE 메시지가 도착하지 않았습니다."
            );

        } finally {
            session1.disconnect();
            session2.disconnect();
            client.stop();
        }

    }

    @Test
    @DisplayName("채팅 채널을 구독하고, 메세지를 전송하면, 같은 채널을 구독한 세션은 메세지를 수신한다. ")
    void chattingSessionTest() throws Exception {
        WebSocketStompClient client = new WebSocketStompClient(new StandardWebSocketClient());
        String contentId = UUID.randomUUID().toString();

        // 테스트용 세션을 생성 한 후, 생성 까지 최대 5초간 대기한다.
        StompSession receiver = getSession(client).get(5, TimeUnit.SECONDS);
        StompSession sender = getSession(client).get(5, TimeUnit.SECONDS);


        try {
            CompletableFuture<String> received = new CompletableFuture<>();

            receiver.subscribe(
                    chattingSessionSubscriptionDst(contentId),
                    getStompHandler(received)
            );
            sender.subscribe(
                    chattingSessionSubscriptionDst(contentId),
                    getStompHandler(received)
            );
            sender.send(
                    chattingSessionSendDst(contentId),
                    new ContentChatSendRequest("this is message.")
            );

            String gettingMessage = received.get(5, TimeUnit.SECONDS);
            log.info("Received message: {}", gettingMessage);

        } finally {
            receiver.disconnect();
            sender.disconnect();
            client.stop();
        }

    }


    @Test
    @DisplayName("컨텐츠가 삭제되었을 때, 관련 컨텐츠에 연결된 세션은 끊긴다.")
    void contentDeleteTest() throws Exception {
        WebSocketStompClient client = new WebSocketStompClient(new StandardWebSocketClient());

        UUID contentId = UUID.randomUUID();


        StompSession session = getSession(client).get(5, TimeUnit.SECONDS);
        try {

            CompletableFuture<String> received = new CompletableFuture<>();

            session.subscribe(
                    viewingSessionDestination(contentId.toString()),
                    getStompHandler(received)
            );

            String message = received.get(5, TimeUnit.SECONDS);
            log.info("Received message: {}", message);

            // 명시적 이벤트 발행을 통한 컨텐츠 삭제 이벤트 시뮬레이션.
            webSocketEventFactory.contentDeleteEvent(contentId);


            // 세션에 대한 끊김 정보 확인
            await()
                    .atMost(5, TimeUnit.SECONDS)
                    .pollInterval(100, TimeUnit.MILLISECONDS)
                    .untilAsserted(() ->
                            assertThat(session.isConnected()).isFalse()
                    );


        } finally {
            if (session.isConnected())
                session.disconnect();
            client.stop();
        }
    }

    @Test
    @DisplayName("유저 로그인에 따른 로그인 해제 기능 실행 시, 유저에 대한 모든 세션이 끊긴다.")
    void userLogoutTest() throws Exception {
        WebSocketStompClient client = new WebSocketStompClient(new StandardWebSocketClient());

        StompSession session = getSession(client).get(5, TimeUnit.SECONDS);
        StompSession session2 = getSession(client).get(5, TimeUnit.SECONDS);
        StompSession session3 = getSession(client).get(5, TimeUnit.SECONDS);

        List<StompSession> sessions = Arrays.asList(session, session2, session3);
        try {
            webSocketEventFactory.authRevokedEvent("admin@admin.com");
            await()
                    .atMost(5, TimeUnit.SECONDS)
                    .pollInterval(100, TimeUnit.MILLISECONDS)
                    .untilAsserted(() ->
                            assertThat(sessions).allMatch(ses -> !ses.isConnected())
                    );

            for (StompSession s : sessions) {
                String t = (s.isConnected() ? "연결됨" : "끊김");
                log.info("session display : {}","세션 결과 - " + t );
            }
        } finally {
            if (session.isConnected())
                session.disconnect();
            if (session2.isConnected())
                session2.disconnect();
            if (session3.isConnected())
                session3.disconnect();
            client.stop();
        }
    }


}
