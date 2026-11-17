package com.codeit.sb13.playlist_for_everyone.domain.conversation.controller;

import com.codeit.sb13.playlist_for_everyone.PlaylistForEveryoneApplication;
import com.codeit.sb13.playlist_for_everyone.domain.auth.event.UserSessionRevokedEvent;
import com.codeit.sb13.playlist_for_everyone.domain.auth.jwt.JwtTokenProvider;
import com.codeit.sb13.playlist_for_everyone.domain.auth.repository.SessionStartResult;
import com.codeit.sb13.playlist_for_everyone.domain.auth.repository.UserSessionRepository;
import com.codeit.sb13.playlist_for_everyone.global.file.storage.FileStorage;
import com.codeit.sb13.playlist_for_everyone.global.websocket.registry.session.WebSocketSessionRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.web.server.context.WebServerApplicationContext;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.messaging.Message;
import org.springframework.messaging.converter.JacksonJsonMessageConverter;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessageType;
import org.springframework.messaging.simp.broker.SimpleBrokerMessageHandler;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;
import org.springframework.web.socket.sockjs.client.SockJsClient;
import org.springframework.web.socket.sockjs.client.WebSocketTransport;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.mockito.Mockito.mock;

@SpringBootTest(
        classes = PlaylistForEveryoneApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "spring.flyway.enabled=true",
                "spring.jpa.hibernate.ddl-auto=validate",
                "spring.jpa.open-in-view=false",
                "spring.docker.compose.enabled=false",
                "everyone.websocket.event.multiple=true",
                "everyone.dm.websocket.transport=redis",
                "everyone.dm.websocket.subscription-enabled=true",
                "everyone.dm.websocket.redis.channel=test:dm:websocket:session-revocation"
        }
)
@Testcontainers
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@DisplayName("DM WebSocket 로그인 세션 무효화 분산 통합 테스트")
class DirectMessageWebSocketSessionRevocationIntegrationTest {

    private static final String AUTH_REVOKE_CHANNEL = "websocket:event:auth:revoke";

    @Container
    static final PostgreSQLContainer postgres =
            new PostgreSQLContainer("postgres:16");

    @Container
    static final GenericContainer<?> redis =
            new GenericContainer<>("redis:7.2.6-alpine")
                    .withExposedPorts(6379);

    @DynamicPropertySource
    static void configureInfrastructure(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.datasource.driver-class-name", postgres::getDriverClassName);
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
    }

    @MockitoBean
    private FileStorage fileStorage;

    @LocalServerPort
    private int port;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @Autowired
    private UserSessionRepository userSessionRepository;

    @Autowired
    private ApplicationEventPublisher applicationEventPublisher;

    @Autowired
    private WebSocketSessionRegistry webSocketSessionRegistry;

    @Autowired
    private SimpleBrokerMessageHandler simpleBrokerMessageHandler;

    @Autowired
    private Environment environment;

    private final UUID userId = UUID.randomUUID();
    private final UUID counterpartId = UUID.randomUUID();
    private final UUID conversationId = UUID.randomUUID();
    private final List<StompSession> stompSessions = new ArrayList<>();
    private UUID loginSessionId;
    private UUID counterpartLoginSessionId;
    private String accessToken;
    private String counterpartAccessToken;
    private WebSocketStompClient stompClient;
    private ConfigurableApplicationContext nodeB;

    @BeforeEach
    void setUp() {
        insertUser(userId, "분산 연결 테스트 사용자");
        insertUser(counterpartId, "대화 상대");
        insertConversation();

        loginSessionId = userSessionRepository.start(userId, Duration.ofMinutes(10)).sessionId();
        counterpartLoginSessionId = userSessionRepository.start(
                counterpartId, Duration.ofMinutes(10)).sessionId();
        accessToken = jwtTokenProvider.createAccessToken(userId, loginSessionId);
        counterpartAccessToken = jwtTokenProvider.createAccessToken(
                counterpartId, counterpartLoginSessionId);

        SockJsClient sockJsClient = new SockJsClient(
                List.of(new WebSocketTransport(new StandardWebSocketClient())));
        stompClient = new WebSocketStompClient(sockJsClient);
        stompClient.setMessageConverter(new JacksonJsonMessageConverter());
        stompClient.start();
    }

    @AfterEach
    void cleanUp() {
        // 정리 중 하나가 실패해도 나머지 자원을 정리하고, B는 클라이언트 종료 이후 닫는다.
        assertAll(
                () -> assertAll(stompSessions.stream().map(session -> () -> {
                    if (session.isConnected()) {
                        session.disconnect();
                    }
                })),
                () -> {
                    if (stompClient != null) {
                        stompClient.stop();
                    }
                },
                () -> {
                    if (nodeB != null) {
                        nodeB.close();
                    }
                },
                () -> {
                    if (loginSessionId != null) {
                        userSessionRepository.endIfCurrent(userId, loginSessionId);
                    }
                },
                () -> {
                    if (counterpartLoginSessionId != null) {
                        userSessionRepository.endIfCurrent(counterpartId, counterpartLoginSessionId);
                    }
                },
                () -> jdbcTemplate.update("DELETE FROM direct_messages WHERE conversation_id = ?", conversationId),
                () -> jdbcTemplate.update("DELETE FROM conversations WHERE id = ?", conversationId),
                () -> jdbcTemplate.update("DELETE FROM users WHERE id IN (?, ?)", userId, counterpartId)
        );
    }

    @Test
    @DisplayName("세션 무효화 이벤트가 서버 간 전파되어 이전 연결을 종료하고 새 로그인과 다른 사용자 연결은 유지한다.")
    void sessionRevoked_closesMatchingConnectionsAcrossServers() throws Exception {
        nodeB = startAdditionalNode();
        int portB = ((WebServerApplicationContext) nodeB).getWebServer().getPort();
        WebSocketSessionRegistry registryB = nodeB.getBean(WebSocketSessionRegistry.class);
        SimpleBrokerMessageHandler brokerB = nodeB.getBean(SimpleBrokerMessageHandler.class);
        assertThat(portB).isNotEqualTo(port);
        assertThat(registryB).isNotSameAs(webSocketSessionRegistry);
        assertThat(brokerB).isNotSameAs(simpleBrokerMessageHandler);

        // DM 메시지 채널과 별개인 인증 무효화 채널에 두 서버가 구독을 마쳤는지 확인한다.
        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
            var subscriptions = redis.execInContainer(
                    "redis-cli", "--raw", "PUBSUB", "NUMSUB", AUTH_REVOKE_CHANNEL);
            assertThat(subscriptions.getExitCode()).isZero();
            assertThat(subscriptions.getStdout().lines().map(String::strip).toList())
                    .containsExactly(AUTH_REVOKE_CHANNEL, "2");
        });

        UUID previousLoginSessionId = loginSessionId;
        StompSession previousConnectionOnA = connectWithToken(port, accessToken);
        StompSession previousConnectionOnB = connectWithToken(portB, accessToken);
        subscribe(previousConnectionOnA, simpleBrokerMessageHandler);
        subscribe(previousConnectionOnB, brokerB);

        SessionStartResult startResult = userSessionRepository.start(userId, Duration.ofMinutes(10));
        UUID newLoginSessionId = startResult.sessionId();
        // 실패 시에도 cleanUp에서 새 로그인 세션을 제거할 수 있도록 먼저 기록한다.
        loginSessionId = newLoginSessionId;
        assertThat(startResult.replaced()).contains(previousLoginSessionId);

        String newAccessToken = jwtTokenProvider.createAccessToken(userId, newLoginSessionId);
        StompSession newConnectionOnB = connectWithToken(portB, newAccessToken);
        StompSession counterpartConnectionOnB = connectWithToken(portB, counterpartAccessToken);
        subscribe(newConnectionOnB, brokerB);
        subscribe(counterpartConnectionOnB, brokerB);

        await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> {
            assertThat(List.of(previousConnectionOnA, previousConnectionOnB,
                    newConnectionOnB, counterpartConnectionOnB))
                    .allSatisfy(session -> assertThat(session.isConnected()).isTrue());
            assertThat(webSocketSessionRegistry.findByUserIdAndSessionId(userId, previousLoginSessionId))
                    .hasSize(1);
            assertThat(registryB.findByUserIdAndSessionId(userId, previousLoginSessionId)).hasSize(1);
            assertThat(registryB.findByUserIdAndSessionId(userId, newLoginSessionId)).hasSize(1);
            assertThat(registryB.findByUserIdAndSessionId(counterpartId, counterpartLoginSessionId)).hasSize(1);
        });

        // A에서 한 번만 발행하여 실제 Redis 전파로 A와 B의 이전 연결이 함께 종료되게 한다.
        applicationEventPublisher.publishEvent(new UserSessionRevokedEvent(
                userId, previousLoginSessionId, UserSessionRevokedEvent.RevokeReason.DUPLICATE_LOGIN));

        await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> {
            assertThat(previousConnectionOnA.isConnected()).isFalse();
            assertThat(previousConnectionOnB.isConnected()).isFalse();
            assertThat(webSocketSessionRegistry.findByUserIdAndSessionId(userId, previousLoginSessionId))
                    .isEmpty();
            assertThat(registryB.findByUserIdAndSessionId(userId, previousLoginSessionId)).isEmpty();
        });

        await().during(Duration.ofSeconds(1)).atMost(Duration.ofSeconds(3)).untilAsserted(() -> {
            assertThat(newConnectionOnB.isConnected()).isTrue();
            assertThat(counterpartConnectionOnB.isConnected()).isTrue();
            assertThat(registryB.findByUserIdAndSessionId(userId, newLoginSessionId)).hasSize(1);
            assertThat(registryB.findByUserIdAndSessionId(counterpartId, counterpartLoginSessionId)).hasSize(1);
            assertThat(userSessionRepository.isCurrent(userId, newLoginSessionId)).isTrue();
            assertThat(userSessionRepository.isCurrent(counterpartId, counterpartLoginSessionId)).isTrue();
        });
    }

    private ConfigurableApplicationContext startAdditionalNode() {
        return new SpringApplicationBuilder(PlaylistForEveryoneApplication.class,
                AdditionalNodeFileStorageConfig.class)
                .registerShutdownHook(false)
                .run(
                        "--spring.profiles.active=test",
                        "--server.port=0",
                        "--spring.application.name=dm-websocket-session-revocation-node-b-test",
                        "--spring.datasource.url=" + postgres.getJdbcUrl(),
                        "--spring.datasource.username=" + postgres.getUsername(),
                        "--spring.datasource.password=" + postgres.getPassword(),
                        "--spring.datasource.driver-class-name=" + postgres.getDriverClassName(),
                        "--spring.flyway.enabled=true",
                        "--spring.jpa.hibernate.ddl-auto=validate",
                        "--spring.jpa.open-in-view=false",
                        "--spring.docker.compose.enabled=false",
                        "--spring.data.redis.host=" + redis.getHost(),
                        "--spring.data.redis.port=" + redis.getMappedPort(6379),
                        "--everyone.jwt.secret-key=" + environment.getRequiredProperty("everyone.jwt.secret-key"),
                        "--everyone.websocket.event.multiple=true",
                        "--everyone.dm.websocket.transport=redis",
                        "--everyone.dm.websocket.subscription-enabled=true",
                        "--everyone.dm.websocket.redis.channel="
                                + environment.getRequiredProperty("everyone.dm.websocket.redis.channel")
                );
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class AdditionalNodeFileStorageConfig {
        @Bean
        @Primary
        FileStorage additionalNodeFileStorage() {
            return mock(FileStorage.class);
        }
    }

    private StompSession connectWithToken(int targetPort, String token)
            throws ExecutionException, InterruptedException, TimeoutException {
        StompHeaders connectHeaders = new StompHeaders();
        connectHeaders.add(HttpHeaders.AUTHORIZATION, "Bearer " + token);
        StompSession session = stompClient.connectAsync(
                "http://localhost:" + targetPort + "/ws",
                new WebSocketHttpHeaders(), connectHeaders, new StompSessionHandlerAdapter() {})
                .get(10, TimeUnit.SECONDS);
        stompSessions.add(session);
        return session;
    }

    private void subscribe(StompSession session, SimpleBrokerMessageHandler broker) {
        String destination = "/sub/conversations/" + conversationId + "/direct-messages";
        String subscriptionId = UUID.randomUUID().toString();
        StompHeaders headers = new StompHeaders();
        headers.setDestination(destination);
        headers.setId(subscriptionId);
        session.subscribe(headers, new StompSessionHandlerAdapter() {});

        SimpMessageHeaderAccessor lookupHeaders = SimpMessageHeaderAccessor.create(SimpMessageType.MESSAGE);
        lookupHeaders.setDestination(destination);
        Message<byte[]> lookupMessage = MessageBuilder.createMessage(new byte[0], lookupHeaders.getMessageHeaders());
        await().atMost(Duration.ofSeconds(5)).until(() -> broker.getSubscriptionRegistry()
                .findSubscriptions(lookupMessage).values().stream()
                .anyMatch(ids -> ids.contains(subscriptionId)));
    }

    private void insertUser(UUID id, String nickname) {
        jdbcTemplate.update("""
                INSERT INTO users (id, email, nickname, role, enabled, created_at)
                VALUES (?, ?, ?, 'USER', true, CURRENT_TIMESTAMP)
                """, id, id + "@test.example", nickname);
    }

    private void insertConversation() {
        boolean userComesFirst = userId.toString().compareTo(counterpartId.toString()) < 0;
        UUID userAId = userComesFirst ? userId : counterpartId;
        UUID userBId = userComesFirst ? counterpartId : userId;
        jdbcTemplate.update("""
                INSERT INTO conversations (id, user_a_id, user_b_id, created_at)
                VALUES (?, ?, ?, CURRENT_TIMESTAMP)
                """, conversationId, userAId, userBId);
    }
}
