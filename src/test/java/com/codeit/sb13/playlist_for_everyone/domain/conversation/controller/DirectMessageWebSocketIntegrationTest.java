package com.codeit.sb13.playlist_for_everyone.domain.conversation.controller;

import com.codeit.sb13.playlist_for_everyone.PlaylistForEveryoneApplication;
import com.codeit.sb13.playlist_for_everyone.domain.auth.event.UserSessionRevokedEvent;
import com.codeit.sb13.playlist_for_everyone.domain.auth.jwt.JwtTokenProvider;
import com.codeit.sb13.playlist_for_everyone.domain.auth.repository.SessionStartResult;
import com.codeit.sb13.playlist_for_everyone.domain.auth.repository.UserSessionRepository;
import com.codeit.sb13.playlist_for_everyone.domain.conversation.dto.request.DirectMessageSendRequest;
import com.codeit.sb13.playlist_for_everyone.domain.conversation.dto.response.MessageDto;
import com.codeit.sb13.playlist_for_everyone.domain.conversation.event.DirectMessagePublisher;
import com.codeit.sb13.playlist_for_everyone.domain.conversation.service.DirectMessageDeliveryService;
import com.codeit.sb13.playlist_for_everyone.global.dto.response.ApiErrorResponse;
import com.codeit.sb13.playlist_for_everyone.global.dto.response.CursorResponse;
import com.codeit.sb13.playlist_for_everyone.global.exception.ApiErrorCode;
import com.codeit.sb13.playlist_for_everyone.global.exception.conversation.DirectMessagePublishException;
import com.codeit.sb13.playlist_for_everyone.global.file.storage.FileStorage;
import com.codeit.sb13.playlist_for_everyone.global.websocket.entity.WSSession;
import com.codeit.sb13.playlist_for_everyone.global.websocket.registry.session.WebSocketSessionRegistry;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.web.server.context.WebServerApplicationContext;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.core.env.Environment;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.messaging.Message;
import org.springframework.messaging.converter.ByteArrayMessageConverter;
import org.springframework.messaging.converter.CompositeMessageConverter;
import org.springframework.messaging.converter.JacksonJsonMessageConverter;
import org.springframework.messaging.converter.MessageConversionException;
import org.springframework.messaging.handler.annotation.support.MethodArgumentNotValidException;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessageType;
import org.springframework.messaging.simp.broker.SimpleBrokerMessageHandler;
import org.springframework.messaging.simp.stomp.*;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.util.MimeTypeUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;
import org.springframework.web.socket.sockjs.client.RestClientXhrTransport;
import org.springframework.web.socket.sockjs.client.SockJsClient;
import org.springframework.web.socket.sockjs.client.WebSocketTransport;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.lang.reflect.Type;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.WebSocket;
import java.net.http.WebSocketHandshakeException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;

/**
 * 실제 HTTP 포트·STOMP 클라이언트·PostgreSQL·Redis로 연결, 송수신, 출처 제한을 검증한다.
 * 로그인 API 대신 실제 Redis 로그인 세션과 서명된 JWT를 준비한다. FileStorage는 Mock이다.
 * Publisher·DeliveryService Spy는 실패 주입·호출 관찰에 사용하며 정상 경로는 실제 구현으로 진행한다.
 * 두 서버 사례의 B는 같은 JVM 안의 독립 Spring 컨텍스트다. Compose 컨테이너를 대상으로 실행하지 않는다.
 * 공통 인증 이벤트의 서버 간 전파는 별도의 SessionRevocationIntegrationTest가 담당한다.
 */
@SpringBootTest(
        classes = PlaylistForEveryoneApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "spring.flyway.enabled=true",
                "spring.jpa.hibernate.ddl-auto=validate",
                "spring.jpa.open-in-view=false",
                "spring.docker.compose.enabled=false",
                "everyone.websocket.allowed-origins=https://frontend.example:443,http://second.example:80",
                "everyone.dm.websocket.transport=redis",
                "everyone.dm.websocket.subscription-enabled=true",
                "everyone.dm.websocket.redis.channel=test:dm:websocket:integration"
        }
)
@Testcontainers
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@DisplayName("DM WebSocket 연결 및 메시지 송수신 통합 테스트")
public class DirectMessageWebSocketIntegrationTest {

    @Container
    static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16");

    @Container
    static final GenericContainer<?> redis = new GenericContainer<>("redis:7.2.6-alpine")
            .withExposedPorts(6379);

    @DynamicPropertySource
    static void configureInfrastructure(DynamicPropertyRegistry registry){
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.datasource.driver-class-name", postgres::getDriverClassName);
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
    }

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
    private ObjectMapper objectMapper;

    @Autowired
    private Environment environment;

    @MockitoSpyBean
    private DirectMessagePublisher directMessagePublisher;

    @MockitoSpyBean
    private DirectMessageDeliveryService directMessageDeliveryService;

    @MockitoBean
    private FileStorage fileStorage;

    private final UUID userId = UUID.randomUUID();
    private UUID loginSessionId;
    private UUID counterpartLoginSessionId;
    private String accessToken;
    private String counterpartAccessToken;
    private WebSocketStompClient stompClient;
    private final List<StompSession> stompSessions = new ArrayList<>();
    private final UUID counterpartId = UUID.randomUUID();
    private final UUID conversationId = UUID.randomUUID();
    private final UUID otherUserId = UUID.randomUUID();
    private final UUID otherConversationId = UUID.randomUUID();


    @BeforeEach
    void setUp() {
        insertUser(userId, "웹소켓 테스트 사용자");
        insertUser(counterpartId, "대화 상대");
        insertUser(otherUserId, "다른 대화 상대");
        insertConversation(conversationId, userId, counterpartId);
        insertConversation(otherConversationId, userId, otherUserId);


        loginSessionId = userSessionRepository.start(userId, Duration.ofMinutes(10))
                .sessionId();
        counterpartLoginSessionId = userSessionRepository.start(counterpartId, Duration.ofMinutes(10))
                .sessionId();

        accessToken = jwtTokenProvider.createAccessToken(userId, loginSessionId);
        counterpartAccessToken = jwtTokenProvider.createAccessToken(counterpartId, counterpartLoginSessionId);
        SockJsClient sockJsClient = new SockJsClient(List.of(new WebSocketTransport(new StandardWebSocketClient())));

        stompClient = new WebSocketStompClient(sockJsClient);
        // 잘못된 JSON도 원문 그대로 보내고, 정상 DTO와 오류 응답은 JSON으로 변환한다.
        ByteArrayMessageConverter rawPayloadConverter = new ByteArrayMessageConverter();
        rawPayloadConverter.setContentTypeResolver(null);
        stompClient.setMessageConverter(new CompositeMessageConverter(List.of(
                rawPayloadConverter,
                new JacksonJsonMessageConverter()
        )));
        stompClient.start();
    }

    @AfterEach
    void cleanUp() {
        try {
            assertAll(
                    stompSessions.stream()
                            .map(session -> () -> {
                                if (session.isConnected()) {
                                    session.disconnect();
                                }
                            })
            );
        } finally {
            if (stompClient != null) {
                stompClient.stop();
            }

            if (loginSessionId != null) {
                userSessionRepository.endIfCurrent(userId, loginSessionId);
            }
            if (counterpartLoginSessionId != null) {
                userSessionRepository.endIfCurrent(counterpartId, counterpartLoginSessionId);
            }

            jdbcTemplate.update("DELETE FROM notifications WHERE receiver_id IN (?, ?, ?)",
                    userId, counterpartId, otherUserId);

            jdbcTemplate.update("DELETE FROM direct_messages WHERE conversation_id IN (? , ?) ",
                    conversationId, otherConversationId);

            jdbcTemplate.update("DELETE FROM conversations WHERE id IN (? , ?)",
                    conversationId, otherConversationId);

            jdbcTemplate.update("DELETE FROM users WHERE id IN (?, ?, ?) ",
                    userId, counterpartId, otherUserId);
        }
    }

    @Test
    @DisplayName("동일 출처에서 유효한 JWT로 CONNECT하면 SockJS 연결이 인증된다.")
    void connect_authenticatesWithJwtInStompHeader()
            throws ExecutionException, InterruptedException, TimeoutException {

        String origin = "http://localhost:" + port;

        WebSocketHttpHeaders handshakeHeaders = new WebSocketHttpHeaders();
        handshakeHeaders.setOrigin(origin);

        StompHeaders connectHeaders = new StompHeaders();
        connectHeaders.add(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken);

        StompSession stompSession = awaitConnection(
                stompClient.connectAsync(
                        origin + "/ws",
                        handshakeHeaders,
                        connectHeaders,
                        new StompSessionHandlerAdapter() {}
                )
        );

        assertThat(stompSession.isConnected()).isTrue();

        await().atMost(Duration.ofSeconds(5))
                .untilAsserted(() ->
                        assertThat(webSocketSessionRegistry.findByUserIdAndLoginSessionId(userId, loginSessionId))
                                .singleElement()
                                .satisfies(session -> {
                                    assertThat(session.socket().isOpen()).isTrue();
                                    assertThat(session.socket().getHandshakeHeaders().getOrigin())
                                            .isEqualTo(origin);
                                }));

    }

    @ParameterizedTest(name = "전송 방식={0}")
    @ValueSource(strings = {"websocket", "xhr"})
    @DisplayName("허용 Origin에 기본 포트를 명시해도 포트 없는 SockJS·XHR 요청은 JWT로 인증된다.")
    void connect_authenticatesAllowedOrigin(String transport)
            throws ExecutionException, InterruptedException, TimeoutException {
        String origin = "https://frontend.example";
        StompSession session;
        if ("xhr".equals(transport)) {
            session = connectOverXhr(origin);
        } else {
            WebSocketHttpHeaders handshakeHeaders = new WebSocketHttpHeaders();
            handshakeHeaders.setOrigin(origin);
            StompHeaders connectHeaders = new StompHeaders();
            connectHeaders.add(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken);
            session = awaitConnection(stompClient.connectAsync(
                    "http://localhost:" + port + "/ws", handshakeHeaders, connectHeaders,
                    new StompSessionHandlerAdapter() {}));
        }

        assertThat(session.isConnected()).isTrue();
        await().atMost(Duration.ofSeconds(5)).untilAsserted(() ->
                assertThat(webSocketSessionRegistry.findByUserIdAndLoginSessionId(userId, loginSessionId))
                        .singleElement()
                        .satisfies(serverSession -> {
                            // XHR 요청은 이미 반환됐을 수 있으므로 요청 헤더 대신 살아 있는 연결과 전송 경로를 확인한다.
                            assertThat(serverSession.socket().isOpen()).isTrue();
                            assertThat(serverSession.socket().getUri()).isNotNull();
                            assertThat(serverSession.socket().getUri().getPath())
                                    .endsWith("/" + transport);
                        }));
    }

    @ParameterizedTest(name = "Origin={0}, 경로={1}")
    @CsvSource({
            "https://frontend.example, /ws",
            "http://second.example, /ws",
            "https://frontend.example, /ws/000/{sessionId}/websocket",
            "http://second.example, /ws/000/{sessionId}/websocket"
    })
    @DisplayName("기본 WebSocket과 SockJS는 허용 Origin의 기본 포트를 생략한 연결도 허용한다.")
    void handshake_allowsConfiguredOrigins(String origin, String path) throws Exception {
        String destination = path.replace("{sessionId}", UUID.randomUUID().toString());
        HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
        CompletableFuture<WebSocket> connection = client.newWebSocketBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .header(HttpHeaders.ORIGIN, origin)
                .buildAsync(URI.create("ws://localhost:" + port + destination), new WebSocket.Listener() {});

        try {
            WebSocket socket = connection.get(10, TimeUnit.SECONDS);
            assertThat(socket.isInputClosed()).isFalse();
        } finally {
            // 늦게 완료되는 연결까지 정리하며 STOMP 업무 메시지는 보내지 않는다.
            connection.thenAccept(WebSocket::abort);
        }
    }

    @Test
    @DisplayName("허용 목록에 없는 출처의 SockJS 연결은 HTTP 403으로 거절되고 인증된 연결이 등록되지 않는다.")
    void connect_rejectsDifferentOriginBeforeStompConnect() {
        WebSocketHttpHeaders handshakeHeaders = new WebSocketHttpHeaders();
        handshakeHeaders.setOrigin("https://other.example");

        StompHeaders connectHeaders = new StompHeaders();
        connectHeaders.add(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken);

        CompletableFuture<StompSession> connection = stompClient.connectAsync(
                "http://localhost:" + port + "/ws",
                handshakeHeaders,
                connectHeaders,
                new StompSessionHandlerAdapter() {}
        );

        assertThatThrownBy(() -> awaitConnection(connection))
                .isInstanceOf(ExecutionException.class)
                .cause()
                .isInstanceOfSatisfying(
                        RestClientResponseException.class,
                        exception -> assertThat(exception.getStatusCode())
                                .isEqualTo(HttpStatus.FORBIDDEN)
                );

        assertThat(webSocketSessionRegistry
                .findByUserIdAndLoginSessionId(userId, loginSessionId))
                .isEmpty();
    }

    @Test
    @DisplayName("동일 출처의 XHR 폴백은 CSRF 토큰 없이 유효한 JWT로 연결된다.")
    void connect_authenticatesOverXhrWithoutCsrfToken()
            throws ExecutionException, InterruptedException, TimeoutException {
        StompSession session = connectOverXhr();

        assertThat(session.isConnected()).isTrue();
        await().atMost(Duration.ofSeconds(5))
                .untilAsserted(() ->
                        assertThat(webSocketSessionRegistry
                                .findByUserIdAndLoginSessionId(userId, loginSessionId))
                                .singleElement()
                                .satisfies(serverSession -> {
                                    assertThat(serverSession.socket().isOpen()).isTrue();
                                    assertThat(serverSession.socket().getUri()).isNotNull();
                                    assertThat(serverSession.socket().getUri().getPath()).endsWith("/xhr");
                                }));
    }

    @Test
    @DisplayName("허용 목록에 없는 출처의 XHR 전송은 403으로 거절되고 기존 연결과 메시지 데이터는 유지된다.")
    void xhrSend_rejectsDifferentOriginForExistingSession()
            throws ExecutionException, InterruptedException, TimeoutException, IOException {
        StompSession session = connectOverXhr();
        List<WSSession> serverSessions = webSocketSessionRegistry
                .findByUserIdAndLoginSessionId(userId, loginSessionId);
        assertThat(serverSessions).hasSize(1);
        WSSession serverSession = serverSessions.get(0);
        URI receiveUri = serverSession.socket().getUri();
        assertThat(receiveUri).isNotNull();
        assertThat(receiveUri.getPath()).endsWith("/xhr");
        assertThat(getSavedMessageIds()).isEmpty();

        StompHeaderAccessor sendHeaders = StompHeaderAccessor.create(StompCommand.SEND);
        sendHeaders.setDestination("/pub/conversations/" + conversationId + "/direct-messages");
        sendHeaders.setContentType(MimeTypeUtils.APPLICATION_JSON);
        byte[] sendFrame = new StompEncoder().encode(
                sendHeaders.getMessageHeaders(),
                objectMapper.writeValueAsBytes(new DirectMessageSendRequest("다른 출처에서 보낸 메시지"))
        );
        String requestBody = objectMapper.writeValueAsString(
                List.of(new String(sendFrame, StandardCharsets.UTF_8)));

        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();
        HttpRequest request = HttpRequest.newBuilder(receiveUri.resolve("xhr_send"))
                .timeout(Duration.ofSeconds(10))
                .header(HttpHeaders.ORIGIN, "https://other.example")
                .header(HttpHeaders.CONTENT_TYPE, "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).as("XHR 전송 응답: %s", response.body())
                .isEqualTo(HttpStatus.FORBIDDEN.value());

        await().during(Duration.ofSeconds(1)).atMost(Duration.ofSeconds(3))
                .untilAsserted(() -> {
                    assertThat(getSavedMessageIds()).isEmpty();
                    assertThat(session.isConnected()).isTrue();
                    assertThat(serverSession.socket().isOpen()).isTrue();
                    assertThat(webSocketSessionRegistry.query(serverSession.id()))
                            .contains(serverSession);
                });
    }

    @ParameterizedTest(name = "다른 출처의 직접 연결 경로: {0}")
    @ValueSource(strings = {"/ws", "/ws/000/{sessionId}/websocket"})
    @DisplayName("WebSocket 전송 경로에 직접 접속해도 허용 목록에 없는 출처의 연결은 403으로 거절된다.")
    void handshake_rejectsDifferentOrigin(String path) {
        String destination = path.replace("{sessionId}", UUID.randomUUID().toString());
        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();
        CompletableFuture<WebSocket> connection = client.newWebSocketBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .header(HttpHeaders.ORIGIN, "https://other.example")
                .buildAsync(URI.create("ws://localhost:" + port + destination), new WebSocket.Listener() {});
        // 예상과 달리 핸드셰이크가 성공해도 테스트용 소켓을 남기지 않는다.
        connection.thenAccept(WebSocket::abort);

        assertThatThrownBy(() -> connection.get(10, TimeUnit.SECONDS))
                .isInstanceOf(ExecutionException.class)
                .cause()
                .isInstanceOfSatisfying(
                        WebSocketHandshakeException.class,
                        exception -> assertThat(exception.getResponse().statusCode())
                                .isEqualTo(HttpStatus.FORBIDDEN.value())
                );
    }

    @Test
    @DisplayName("JWT 없이 CONNECT하면 ERROR 프레임을 수신하고 연결이 종료된다.")
    void connect_rejectsMissingJwt() throws ExecutionException, InterruptedException, TimeoutException {
        CompletableFuture<StompHeaders> receivedError = new CompletableFuture<>();

        CompletableFuture<StompSession> connection = stompClient.connectAsync(
                "http://localhost:" + port + "/ws",
                new WebSocketHttpHeaders(),
                new StompHeaders(),
                new StompSessionHandlerAdapter() {
                    @Override
                    public void handleFrame(StompHeaders headers, @Nullable Object payload) {
                        receivedError.complete(headers);
                    }
                }
        );

        assertThatThrownBy(() -> awaitConnection(connection))
                .isInstanceOf(ExecutionException.class)
                .hasCauseInstanceOf(ConnectionLostException.class);

        StompHeaders errorHeaders = receivedError.get(10, TimeUnit.SECONDS);
        assertThat(errorHeaders.getFirst("message")).isNotBlank();
        assertThat(webSocketSessionRegistry.findByUserIdAndLoginSessionId(userId, loginSessionId))
                .isEmpty();
    }

    @Test
    @DisplayName("로그인 세션이 무효화된 JWT로 CONNECT하면 ERROR를 수신하고 연결이 종료된다.")
    void connect_rejectsRevokedLoginSession() throws ExecutionException, InterruptedException, TimeoutException {
        userSessionRepository.revoke(userId);
        StompHeaders connectHeaders = new StompHeaders();

        connectHeaders.add(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken);
        CompletableFuture<StompHeaders> receivedError = new CompletableFuture<>();

        CompletableFuture<StompSession> connection = stompClient.connectAsync(
                "http://localhost:" + port + "/ws",
                new WebSocketHttpHeaders(),
                connectHeaders,
                new StompSessionHandlerAdapter() {
                    @Override
                    public void handleFrame(StompHeaders headers, @Nullable Object payload) {
                        receivedError.complete(headers);
                    }
                }
        );

        assertThatThrownBy(() -> awaitConnection(connection))
                .isInstanceOf(ExecutionException.class)
                .hasCauseInstanceOf(ConnectionLostException.class);

        StompHeaders errorHeaders = receivedError.get(10, TimeUnit.SECONDS);
        assertThat(errorHeaders.getFirst("message"))
                .isNotBlank();
        assertThat(webSocketSessionRegistry.findByUserIdAndLoginSessionId(userId, loginSessionId))
                .isEmpty();
    }

    @Test
    @DisplayName("로그인 세션이 무효화되면 해당 세션의 모든 연결을 종료하고 다른 사용자 연결은 유지한다.")
    void sessionRevoked_closesAllMatchingConnectionsAndKeepsOtherUserConnected()
            throws ExecutionException, InterruptedException, TimeoutException {

        List<StompSession> revokedConnections = List.of(
                connectWithToken(accessToken),
                connectWithToken(accessToken)
        );
        StompSession counterpartConnection = connectWithToken(counterpartAccessToken);

        for (StompSession connection : revokedConnections) {
            subscribe(connection, conversationId);
        }
        subscribe(counterpartConnection, conversationId);

        await().atMost(Duration.ofSeconds(5))
                .untilAsserted(() -> {
                    assertThat(webSocketSessionRegistry
                            .findByUserIdAndLoginSessionId(userId, loginSessionId))
                            .hasSize(2);
                    assertThat(webSocketSessionRegistry
                            .findByUserIdAndLoginSessionId(
                                    counterpartId, counterpartLoginSessionId))
                            .hasSize(1);
                });

        assertThat(userSessionRepository.revoke(userId))
                .contains(loginSessionId);

        applicationEventPublisher.publishEvent(
                new UserSessionRevokedEvent(
                        userId,
                        loginSessionId,
                        UserSessionRevokedEvent.RevokeReason.ROLE_CHANGED
                )
        );

        await().atMost(Duration.ofSeconds(5))
                .untilAsserted(() -> {
                    assertThat(revokedConnections)
                            .allSatisfy(connection ->
                                    assertThat(connection.isConnected()).isFalse());

                    assertThat(webSocketSessionRegistry
                            .findByUserIdAndLoginSessionId(userId, loginSessionId))
                            .isEmpty();
                });

        await().during(Duration.ofSeconds(1))
                .atMost(Duration.ofSeconds(3))
                .untilAsserted(() -> {
                    assertThat(counterpartConnection.isConnected()).isTrue();
                    assertThat(webSocketSessionRegistry
                            .findByUserIdAndLoginSessionId(
                                    counterpartId, counterpartLoginSessionId))
                            .hasSize(1);
                });
    }

    @Test
    @DisplayName("DM을 전송하면 한 건을 저장하고 양쪽 참여자에게 전달하며 미읽음 상태를 유지한다.")
    void send_persistsOnceAndDeliversToBothParticipants() throws ExecutionException, InterruptedException, TimeoutException {
        StompSession senderSession = connectWithToken(accessToken);
        StompSession receiverSession = connectWithToken(counterpartAccessToken);

        BlockingQueue<MessageDto> senderMessages = subscribe(senderSession, conversationId);
        BlockingQueue<MessageDto> receiverMessages = subscribe(receiverSession, conversationId);

        String content ="안녕하세요.";
        senderSession.send("/pub/conversations/"+conversationId + "/direct-messages",
                new DirectMessageSendRequest(content));

        MessageDto senderMessage = senderMessages.poll(10, TimeUnit.SECONDS);
        MessageDto receiverMessage = receiverMessages.poll(10, TimeUnit.SECONDS);

        assertThat(senderMessage).isNotNull();
        assertThat(receiverMessage).isEqualTo(senderMessage);
        assertThat(senderMessage.id()).isNotNull();
        assertThat(senderMessage.createdAt()).isNotNull();
        assertThat(senderMessage.conversationId()).isEqualTo(conversationId);
        assertThat(senderMessage.content()).isEqualTo(content);
        assertThat(senderMessage.sender().userId()).isEqualTo(userId);
        assertThat(senderMessage.receiver().userId()).isEqualTo(counterpartId);

        List<UUID> savedMessageIds = getSavedMessageIds();

        assertThat(savedMessageIds).containsExactly(senderMessage.id());

        Boolean read = jdbcTemplate.queryForObject("""
                    SELECT is_read
                    FROM direct_messages
                    WHERE id =?
                """, Boolean.class, senderMessage.id());
        assertThat(read).isFalse();
    }

    @Test
    @DisplayName("양쪽 참여자가 여러 연결로 구독하면 모든 연결에 메시지를 중복 없이 전달한다.")
    void send_deliversOnceToEverySubscribedConnection() throws ExecutionException, InterruptedException, TimeoutException {
        StompSession senderSession = connectWithToken(accessToken);
        StompSession senderSecondSession = connectWithToken(accessToken);
        StompSession receiverSession = connectWithToken(counterpartAccessToken);
        StompSession receiverSecondSession = connectWithToken(counterpartAccessToken);

        List<BlockingQueue<MessageDto>> queues = List.of(
                subscribe(senderSession, conversationId),
                subscribe(senderSecondSession, conversationId),
                subscribe(receiverSession, conversationId),
                subscribe(receiverSecondSession, conversationId)
        );

        String content ="여러 탭에 전달할 메시지";
        senderSession.send("/pub/conversations/" + conversationId + "/direct-messages",
                new DirectMessageSendRequest(content));

        MessageDto expected = queues.get(0).poll(10, TimeUnit.SECONDS);
        assertThat(expected).isNotNull();
        assertThat(expected.content()).isEqualTo(content);
        assertThat(expected.conversationId()).isEqualTo(conversationId);

        for (int i = 1; i < queues.size(); i++) {
            MessageDto received = queues.get(i).poll(10, TimeUnit.SECONDS);
            assertThat(received).isEqualTo(expected);
        }

        await().during(Duration.ofSeconds(1))
                .atMost(Duration.ofSeconds(3))
                .untilAsserted(() ->
                        queues.forEach(queue ->
                                assertThat(queue).isEmpty()));

        List<UUID> savedMessageIds = getSavedMessageIds();
        assertThat(savedMessageIds).containsExactly(expected.id());
    }

    @Test
    @DisplayName("두 대화방을 구독해도 메시지는 전송한 대화방에만 전달한다.")
    void send_doesNotDeliverToAnotherConversation() throws ExecutionException, InterruptedException, TimeoutException {
        StompSession session = connectWithToken(accessToken);
        BlockingQueue<MessageDto> targetMessages = subscribe(session, conversationId);
        BlockingQueue<MessageDto> otherMessages = subscribe(session, otherConversationId);

        String content = "기존 대화방에만 전달할 메시지";
        session.send("/pub/conversations/" + conversationId + "/direct-messages",
                new DirectMessageSendRequest(content));

        MessageDto received = targetMessages.poll(10, TimeUnit.SECONDS);
        assertThat(received).isNotNull();
        assertThat(received.content()).isEqualTo(content);
        assertThat(received.conversationId()).isEqualTo(conversationId);

        await().during(Duration.ofSeconds(1))
                .atMost(Duration.ofSeconds(3))
                .untilAsserted(() -> assertThat(otherMessages).isEmpty());
        assertThat(session.isConnected()).isTrue();
    }

    @Test
    @DisplayName("비참여자가 DM을 전송하면 요청 연결에만 오류를 전달하고 메시지를 저장하지 않는다.")
    void send_rejectsNonParticipantWithoutAffectingOtherConnections() throws ExecutionException, InterruptedException, TimeoutException {
        CompletableFuture<ApiErrorResponse> requesterError = new CompletableFuture<>();
        CompletableFuture<ApiErrorResponse> participantError = new CompletableFuture<>();

        StompSession participantSession = connectWithToken(accessToken, errorHandler(participantError));
        BlockingQueue<MessageDto> participantMessages = subscribe(participantSession, otherConversationId);
        StompSession requesterSession = connectWithToken(counterpartAccessToken, errorHandler(requesterError));

        requesterSession.send("/pub/conversations/"+ otherConversationId + "/direct-messages",
                new DirectMessageSendRequest("참여 하지 않은 대화방으로 전송"));

        ApiErrorResponse error = requesterError.get(10, TimeUnit.SECONDS);
        assertThat(error).isNotNull();
        assertThat(error.exceptionName()).isEqualTo(AccessDeniedException.class.getSimpleName());
        assertThat(error.details()).isEmpty();
        await().atMost(Duration.ofSeconds(5))
                .untilAsserted(() -> assertThat(requesterSession.isConnected()).isFalse());
        await().during(Duration.ofSeconds(1))
                .atMost(Duration.ofSeconds(3))
                .untilAsserted(() -> {
                    assertThat(participantSession.isConnected()).isTrue();
                    assertThat(participantError.isDone()).isFalse();
                    assertThat(participantMessages).isEmpty();
                });

        Long savedCount = jdbcTemplate.queryForObject("""
                    SELECT COUNT(*)
                    FROM direct_messages
                    WHERE conversation_id = ?
                """, Long.class, otherConversationId);
        assertThat(savedCount).isZero();
    }

    @Test
    @DisplayName("비참여자가 DM을 구독하면 요청 연결에만 오류를 전달하고 연결을 종료한다.")
    void subscribe_rejectsNonParticipantWithoutAffectingOtherConnections() throws ExecutionException, InterruptedException, TimeoutException {
        CompletableFuture<ApiErrorResponse> requesterError = new CompletableFuture<>();
        CompletableFuture<ApiErrorResponse> participantError = new CompletableFuture<>();

        StompSession participantSession = connectWithToken(accessToken, errorHandler(participantError));
        BlockingQueue<MessageDto> participantMessages = subscribe(participantSession, otherConversationId);

        StompSession requesterSession = connectWithToken(counterpartAccessToken, errorHandler(requesterError));
        LinkedBlockingQueue<MessageDto> requesterMessages = new LinkedBlockingQueue<>();

        requesterSession.subscribe("/sub/conversations/"+ otherConversationId + "/direct-messages",
                messageHandler(requesterMessages));

        ApiErrorResponse error = requesterError.get(10, TimeUnit.SECONDS);

        assertThat(error).isNotNull();
        assertThat(error.exceptionName())
                .isEqualTo(AccessDeniedException.class.getSimpleName());
        assertThat(error.message()).isEqualTo("접근 권한이 없습니다.");
        assertThat(error.details()).isEmpty();
        await().atMost(Duration.ofSeconds(5))
                .untilAsserted(() ->
                        assertThat(requesterSession.isConnected()).isFalse());

        assertThat(requesterSession.isConnected()).isFalse();
        await().during(Duration.ofSeconds(1))
                .atMost(Duration.ofSeconds(3))
                .untilAsserted(() ->{
                    assertThat(participantSession.isConnected()).isTrue();
                    assertThat(participantError.isDone()).isFalse();
                    assertThat(participantMessages).isEmpty();
                    assertThat(requesterMessages).isEmpty();
                });
    }

    @Test
    @DisplayName("본문과 헤더의 발신자·수신자 ID를 위조해도 인증 사용자와 실제 대화 상대를 사용한다.")
    void send_ignoresSpoofedSenderAndReceiverInPayloadAndHeaders() throws ExecutionException, InterruptedException, TimeoutException {
        StompSession senderSession = connectWithToken(accessToken);
        StompSession receiverSession = connectWithToken(counterpartAccessToken);

        BlockingQueue<MessageDto> receivedMessages = subscribe(receiverSession, conversationId);
        String content = "사용자 ID 위조 검증";

        StompHeaders headers = new StompHeaders();
        headers.setDestination("/pub/conversations/" + conversationId + "/direct-messages");
        headers.add("senderId", counterpartId.toString());
        headers.add("receiverId", otherUserId.toString());

        senderSession.send(headers,
                Map.of(
                        "content", content,
                        "senderId", counterpartId.toString(),
                        "receiverId", otherUserId.toString()
                ));

        MessageDto received = receivedMessages.poll(10, TimeUnit.SECONDS);
        assertThat(received).isNotNull();
        assertThat(received.conversationId()).isEqualTo(conversationId);
        assertThat(received.content()).isEqualTo(content);
        assertThat(received.sender().userId()).isEqualTo(userId);
        assertThat(received.receiver().userId()).isEqualTo(counterpartId);

        Map<String, Object> savedMessage = jdbcTemplate.queryForMap("""
                SELECT sender_id, receiver_id
                FROM direct_messages
                WHERE id = ?
                """, received.id());

        assertThat(savedMessage)
                .containsEntry("sender_id", userId)
                .containsEntry("receiver_id", counterpartId);
    }

    @Test
    @DisplayName("공백 메시지를 전송하면 요청 연결에만 검증 오류를 전달하고 저장하지 않는다.")
    void send_rejectsBlankContentWithoutAffectingOtherConnections()
            throws ExecutionException, InterruptedException, TimeoutException {
        ApiErrorResponse error = sendAndReceiveError(new DirectMessageSendRequest("   "));

        assertThat(error.exceptionName())
                .isEqualTo(MethodArgumentNotValidException.class.getSimpleName());
        assertThat(error.message()).isEqualTo(ApiErrorCode.INVALID_REQUEST.getMessage());
        assertThat(error.details()).containsKey("content");
        assertThat(getSavedMessageIds()).isEmpty();
    }

    @Test
    @DisplayName("잘못된 JSON을 전송하면 요청 연결에만 변환 오류를 전달하고 저장하지 않는다.")
    void send_rejectsMalformedJsonWithoutAffectingOtherConnections()
            throws ExecutionException, InterruptedException, TimeoutException {
        ApiErrorResponse error = sendAndReceiveError(
                "{invalid-json}".getBytes(StandardCharsets.UTF_8));

        assertThat(error.exceptionName()).isEqualTo(MessageConversionException.class.getSimpleName());
        assertThat(error.message()).isEqualTo(ApiErrorCode.INVALID_REQUEST.getMessage());
        assertThat(error.details()).isEmpty();
        assertThat(getSavedMessageIds()).isEmpty();
    }

    @Test
    @DisplayName("DB 저장에 실패하면 요청 연결에만 오류를 전달하고 저장을 롤백한다.")
    void send_reportsDatabaseFailureWithoutAffectingOtherConnections()
            throws ExecutionException, InterruptedException, TimeoutException {
        // JSON과 Bean Validation은 통과하지만 PostgreSQL text에는 저장할 수 없는 문자다.
        ApiErrorResponse error = sendAndReceiveError(
                new DirectMessageSendRequest("DB 저장 실패 검증" + (char) 0));

        assertThat(error.exceptionName()).isEqualTo(DataIntegrityViolationException.class.getSimpleName());
        assertThat(error.message()).isEqualTo(ApiErrorCode.INTERNAL_SERVER_ERROR.getMessage());
        assertThat(error.details()).isEmpty();
        assertThat(getSavedMessageIds()).isEmpty();
    }

    @Test
    @DisplayName("발행에 실패하면 요청 연결에 저장 완료 오류를 전달하고 저장된 DM은 REST로 조회된다.")
    void send_preservesMessageForRestLookupWhenPublishingFails()
            throws IOException, ExecutionException, InterruptedException, TimeoutException {
        // 발행 경계만 실패시켜 인증과 REST 조회에 사용하는 실제 Redis 연결은 유지한다.
        doAnswer(invocation -> {
            MessageDto message = invocation.getArgument(0);
            throw new DirectMessagePublishException(message.id(), message.conversationId(),
                    new IllegalStateException("테스트용 Redis 발행 실패"));
        }).when(directMessagePublisher).publish(any(MessageDto.class));

        String content = "발행 실패 후에도 조회할 메시지";
        ApiErrorResponse error = sendAndReceiveError(new DirectMessageSendRequest(content));

        assertThat(error.exceptionName()).isEqualTo(DirectMessagePublishException.class.getSimpleName());
        assertThat(error.message()).isEqualTo(ApiErrorCode.DIRECT_MESSAGE_PUBLISH_FAILED.getMessage());
        List<UUID> savedIds = getSavedMessageIds();
        assertThat(savedIds).hasSize(1);
        UUID savedId = savedIds.get(0);
        assertThat(error.details()).containsExactlyInAnyOrderEntriesOf(Map.of(
                "directMessageId", savedId.toString(),
                "conversationId", conversationId.toString(),
                "persisted", true
        ));

        assertMessageAvailableThroughRest(savedId, content);
        assertSavedUnreadMessage(savedId, content);
    }

    @Test
    @DisplayName("구독자 없이 전송해도 정상 처리하고 연결 종료 후 저장된 DM을 REST로 조회한다.")
    void send_withoutSubscribersRemainsAvailableThroughRestAfterDisconnect()
            throws IOException, ExecutionException, InterruptedException, TimeoutException {
        CompletableFuture<MessageDto> delivered = new CompletableFuture<>();
        doAnswer(invocation -> {
            try {
                Object result = invocation.callRealMethod();
                delivered.complete(invocation.getArgument(0, MessageDto.class));
                return result;
            } catch (Throwable exception) {
                delivered.completeExceptionally(exception);
                throw exception;
            }
        }).when(directMessageDeliveryService).sendToLocalSubscribers(any(MessageDto.class));

        CompletableFuture<ApiErrorResponse> receivedError = new CompletableFuture<>();
        StompSession session = connectWithToken(accessToken, errorHandler(receivedError));
        String content = "구독자가 없어도 보관할 메시지";
        session.send("/pub/conversations/" + conversationId + "/direct-messages",
                new DirectMessageSendRequest(content));

        // Redis 수신과 실제 로컬 전달 반환까지 기다려 DB 저장 직후의 중간 상태를 피한다.
        MessageDto message = delivered.get(10, TimeUnit.SECONDS);
        assertThat(message.conversationId()).isEqualTo(conversationId);
        await().during(Duration.ofSeconds(1)).atMost(Duration.ofSeconds(3))
                .untilAsserted(() -> {
                    assertThat(session.isConnected()).isTrue();
                    assertThat(receivedError.isDone()).isFalse();
                });

        session.disconnect();
        await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> {
            assertThat(session.isConnected()).isFalse();
            assertThat(webSocketSessionRegistry.findByUserIdAndLoginSessionId(userId, loginSessionId)).isEmpty();
        });

        assertMessageAvailableThroughRest(message.id(), content);
        assertSavedUnreadMessage(message.id(), content);
    }

    @Test
    @DisplayName("서버 A에서 보낸 DM이 서버 A와 B의 실제 구독자에게 한 번씩 전달되고 미읽음 한 건으로 유지된다.")
    void send_deliversAcrossActualServersWithoutDuplicatingMessages() throws Exception {
        try (ConfigurableApplicationContext nodeB = startAdditionalNode()) {
            int portB = ((WebServerApplicationContext) nodeB).getWebServer().getPort();
            SimpleBrokerMessageHandler brokerB = nodeB.getBean(SimpleBrokerMessageHandler.class);
            assertThat(portB).isNotEqualTo(port);
            assertThat(brokerB).isNotSameAs(simpleBrokerMessageHandler);

            String channel = environment.getRequiredProperty("everyone.dm.websocket.redis.channel");
            await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
                var subscriptions = redis.execInContainer("redis-cli", "--raw", "PUBSUB", "NUMSUB", channel);
                assertThat(subscriptions.getExitCode()).isZero();
                assertThat(subscriptions.getStdout().lines().map(String::strip).toList())
                        .containsExactly(channel, "2");
            });

            // B가 실행 중인 동안 두 클라이언트를 먼저 닫고, 마지막에 B 컨텍스트를 정리한다.
            try {
                StompSession sender = connectWithToken(accessToken);
                StompSession receiver = connectWithToken(portB, counterpartAccessToken,
                        new StompSessionHandlerAdapter() {});
                BlockingQueue<MessageDto> messagesOnA = subscribe(sender, conversationId);
                BlockingQueue<MessageDto> messagesOnB = subscribe(receiver, conversationId, brokerB);
                String content = "서버 A에서 B로 전달할 메시지";

                sender.send("/pub/conversations/" + conversationId + "/direct-messages",
                        new DirectMessageSendRequest(content));

                MessageDto messageOnA = messagesOnA.poll(10, TimeUnit.SECONDS);
                MessageDto messageOnB = messagesOnB.poll(10, TimeUnit.SECONDS);
                assertThat(messageOnA).isNotNull();
                assertThat(messageOnB).isEqualTo(messageOnA);
                assertThat(messageOnB.conversationId()).isEqualTo(conversationId);
                assertThat(messageOnB.content()).isEqualTo(content);
                assertThat(messageOnB.sender().userId()).isEqualTo(userId);
                assertThat(messageOnB.receiver().userId()).isEqualTo(counterpartId);

                await().during(Duration.ofSeconds(1)).atMost(Duration.ofSeconds(3))
                        .untilAsserted(() -> {
                            assertThat(sender.isConnected()).isTrue();
                            assertThat(receiver.isConnected()).isTrue();
                            assertThat(messagesOnA).isEmpty();
                            assertThat(messagesOnB).isEmpty();
                        });
                assertSavedUnreadMessage(messageOnB.id(), content);
            } finally {
                assertAll(stompSessions.stream().map(session -> () -> {
                    if (session.isConnected()) {
                        session.disconnect();
                    }
                }));
            }
        }
    }

    @Test
    @DisplayName("이전 로그인 세션을 무효화해도 같은 사용자의 새 로그인 연결은 유지한다.")
    void sessionRevoked_keepsNewLoginConnectionsForSameUser() throws ExecutionException, InterruptedException, TimeoutException {
        UUID previousLoginSessionId = loginSessionId;
        StompSession previousConnection = connectWithToken(accessToken);
        subscribe(previousConnection, conversationId);

        SessionStartResult startResult = userSessionRepository.start(userId, Duration.ofMinutes(10));

        UUID newLoginSessionId = startResult.sessionId();
        loginSessionId = newLoginSessionId;
        assertThat(startResult.replaced())
                .contains(previousLoginSessionId);

        String newAccessToken = jwtTokenProvider.createAccessToken(userId, newLoginSessionId);
        StompSession newConnection = connectWithToken(newAccessToken);
        subscribe(newConnection, conversationId);

        await().atMost(Duration.ofSeconds(5))
                .untilAsserted(() -> {
                    assertThat(previousConnection.isConnected()).isTrue();
                    assertThat(newConnection.isConnected()).isTrue();
                    assertThat(webSocketSessionRegistry.findByUserIdAndLoginSessionId(userId, previousLoginSessionId))
                            .hasSize(1);
                    assertThat(webSocketSessionRegistry.findByUserIdAndLoginSessionId(userId, newLoginSessionId))
                            .hasSize(1);

                });

        applicationEventPublisher.publishEvent(
                new UserSessionRevokedEvent(
                        userId,
                        previousLoginSessionId,
                        UserSessionRevokedEvent.RevokeReason.DUPLICATE_LOGIN
                )
        );

        await().atMost(Duration.ofSeconds(5))
                        .untilAsserted(() ->{
                            assertThat(previousConnection.isConnected()).isFalse();
                            assertThat(webSocketSessionRegistry.findByUserIdAndLoginSessionId(userId, previousLoginSessionId))
                                    .isEmpty();
                        });

        await().during(Duration.ofSeconds(1))
                .atMost(Duration.ofSeconds(3))
                .untilAsserted(() -> {
                    assertThat(newConnection.isConnected()).isTrue();
                    assertThat(webSocketSessionRegistry.findByUserIdAndLoginSessionId(userId, newLoginSessionId))
                            .hasSize(1);
                    assertThat(userSessionRepository.isCurrent(userId, newLoginSessionId)).isTrue();
                });
    }


    private ConfigurableApplicationContext startAdditionalNode() {
        // CLI 속성으로 전달하여 application-test.yaml의 H2/create-drop 설정보다 우선한다.
        return new SpringApplicationBuilder(PlaylistForEveryoneApplication.class,
                AdditionalNodeFileStorageConfig.class)
                .registerShutdownHook(false)
                .run(
                        "--spring.profiles.active=test",
                        "--server.port=0",
                        "--spring.application.name=dm-websocket-node-b-test",
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

    private ApiErrorResponse sendAndReceiveError(Object payload)
            throws ExecutionException, InterruptedException, TimeoutException {
        CompletableFuture<ApiErrorResponse> requesterError = new CompletableFuture<>();
        CompletableFuture<ApiErrorResponse> participantError = new CompletableFuture<>();
        StompSession participantSession = connectWithToken(counterpartAccessToken, errorHandler(participantError));
        BlockingQueue<MessageDto> participantMessages = subscribe(participantSession, conversationId);
        StompSession requesterSession = connectWithToken(accessToken, errorHandler(requesterError));

        StompHeaders headers = new StompHeaders();
        headers.setDestination("/pub/conversations/" + conversationId + "/direct-messages");
        headers.setContentType(MimeTypeUtils.APPLICATION_JSON);
        requesterSession.send(headers, payload);

        ApiErrorResponse error = requesterError.get(10, TimeUnit.SECONDS);
        assertThat(error).isNotNull();
        await().atMost(Duration.ofSeconds(5))
                .untilAsserted(() -> assertThat(requesterSession.isConnected()).isFalse());
        await().during(Duration.ofSeconds(1)).atMost(Duration.ofSeconds(3))
                .untilAsserted(() -> {
                    assertThat(participantSession.isConnected()).isTrue();
                    assertThat(participantError.isDone()).isFalse();
                    assertThat(participantMessages).isEmpty();
                });
        return error;
    }

    private void assertMessageAvailableThroughRest(UUID messageId, String content)
            throws IOException, InterruptedException {
        HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
        URI uri = URI.create("http://localhost:" + port + "/api/conversations/" + conversationId
                + "/direct-messages?limit=20&sortDirection=DESCENDING&sortBy=createdAt");
        HttpRequest request = HttpRequest.newBuilder(uri)
                .timeout(Duration.ofSeconds(10))
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + counterpartAccessToken)
                .GET()
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).as("REST 응답 본문: %s", response.body()).isEqualTo(200);
        CursorResponse<MessageDto> body = objectMapper.readValue(response.body(), new TypeReference<>() {});
        assertThat(body.totalCount()).isEqualTo(1);
        assertThat(body.hasNext()).isFalse();
        assertThat(body.data()).singleElement().satisfies(message -> {
            assertThat(message.id()).isEqualTo(messageId);
            assertThat(message.conversationId()).isEqualTo(conversationId);
            assertThat(message.content()).isEqualTo(content);
            assertThat(message.createdAt()).isNotNull();
            assertThat(message.sender().userId()).isEqualTo(userId);
            assertThat(message.receiver().userId()).isEqualTo(counterpartId);
        });
    }

    private void assertSavedUnreadMessage(UUID messageId, String content) {
        assertThat(getSavedMessageIds()).containsExactly(messageId);
        Map<String, Object> saved = jdbcTemplate.queryForMap("""
                SELECT conversation_id, sender_id, receiver_id, content, is_read
                FROM direct_messages
                WHERE id = ?
                """, messageId);
        assertThat(saved)
                .containsEntry("conversation_id", conversationId)
                .containsEntry("sender_id", userId)
                .containsEntry("receiver_id", counterpartId)
                .containsEntry("content", content)
                .containsEntry("is_read", false);
    }

    private StompSessionHandler errorHandler(CompletableFuture<ApiErrorResponse> receivedError) {
        return new StompSessionHandlerAdapter() {
            @Override
            public Type getPayloadType(StompHeaders headers) {
                return ApiErrorResponse.class;
            }

            @Override
            public void handleFrame(StompHeaders headers, @Nullable Object payload) {
                receivedError.complete((ApiErrorResponse) payload);
            }

            @Override
            public void handleException(StompSession session, @Nullable StompCommand command, StompHeaders headers, byte[] payload, Throwable exception) {
                receivedError.completeExceptionally(exception);
            }
        };
    }

    private List<UUID> getSavedMessageIds() {
        return jdbcTemplate.queryForList("""
                    SELECT id
                    FROM direct_messages
                    WHERE conversation_id = ?
                """, UUID.class, conversationId);
    }

    private BlockingQueue<MessageDto> subscribe(StompSession session, UUID targetConversationId) {
        return subscribe(session, targetConversationId, simpleBrokerMessageHandler);
    }

    private BlockingQueue<MessageDto> subscribe(
            StompSession session,
            UUID targetConversationId,
            SimpleBrokerMessageHandler broker
    ) {
        LinkedBlockingQueue<MessageDto> receivedMessages = new LinkedBlockingQueue<>();

        String destination = "/sub/conversations/" + targetConversationId + "/direct-messages";
        String subscriptionId = UUID.randomUUID().toString();

        StompHeaders headers = new StompHeaders();
        headers.setDestination(destination);
        headers.setId(subscriptionId);

        session.subscribe(headers, messageHandler(receivedMessages));
        SimpMessageHeaderAccessor lookupHeaders = SimpMessageHeaderAccessor.create(SimpMessageType.MESSAGE);
        lookupHeaders.setDestination(destination);

        Message<byte[]> lookupMessage = MessageBuilder.createMessage(new byte[0], lookupHeaders.getMessageHeaders());
        await().atMost(Duration.ofSeconds(5))
                .until(() -> broker.getSubscriptionRegistry()
                        .findSubscriptions(lookupMessage)
                        .values()
                        .stream().anyMatch(ids -> ids.contains(subscriptionId)));

        return receivedMessages;
    }

    private StompFrameHandler messageHandler(BlockingQueue<MessageDto> receivedMessages) {
        return new StompFrameHandler() {
            @Override
            public Type getPayloadType(StompHeaders headers) {
                return MessageDto.class;
            }

            @Override
            public void handleFrame(
                    StompHeaders headers,
                    @Nullable
                    Object payload
            ) {
                receivedMessages.add((MessageDto) payload);
            }
        };
    }


    private StompSession connectOverXhr()
            throws ExecutionException, InterruptedException, TimeoutException {
        return connectOverXhr("http://localhost:" + port);
    }

    private StompSession connectOverXhr(String origin)
            throws ExecutionException, InterruptedException, TimeoutException {
        stompClient.stop();

        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(
                HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build());
        requestFactory.setReadTimeout(Duration.ofSeconds(10));
        RestClientXhrTransport xhrTransport = new RestClientXhrTransport(
                RestClient.builder().requestFactory(requestFactory).build());
        xhrTransport.setXhrStreamingDisabled(true);

        SockJsClient sockJsClient = new SockJsClient(List.of(xhrTransport));
        stompClient = new WebSocketStompClient(sockJsClient);
        stompClient.setMessageConverter(new JacksonJsonMessageConverter());
        stompClient.start();

        WebSocketHttpHeaders handshakeHeaders = new WebSocketHttpHeaders();
        handshakeHeaders.setOrigin(origin);
        StompHeaders connectHeaders = new StompHeaders();
        connectHeaders.add(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken);

        return awaitConnection(stompClient.connectAsync(
                "http://localhost:" + port + "/ws",
                handshakeHeaders,
                connectHeaders,
                new StompSessionHandlerAdapter() {}
        ));
    }

    private StompSession connectWithToken(String token) throws ExecutionException, InterruptedException, TimeoutException {
        return connectWithToken(token, new StompSessionHandlerAdapter(){});
    }

    private StompSession connectWithToken(String token, StompSessionHandler sessionHandler) throws ExecutionException,
            InterruptedException, TimeoutException {
        return connectWithToken(port, token, sessionHandler);
    }

    private StompSession connectWithToken(int targetPort, String token, StompSessionHandler sessionHandler)
            throws ExecutionException, InterruptedException, TimeoutException {

        StompHeaders connectHeaders = new StompHeaders();

        connectHeaders.add(HttpHeaders.AUTHORIZATION, "Bearer " + token);

        return awaitConnection(
                stompClient.connectAsync(
                        "http://localhost:" + targetPort + "/ws",
                        new WebSocketHttpHeaders(),
                        connectHeaders,
                        sessionHandler));
    }

    private StompSession awaitConnection(
            CompletableFuture<StompSession> connection
    ) throws ExecutionException, InterruptedException, TimeoutException {
        StompSession session = connection.get(10, TimeUnit.SECONDS);
        stompSessions.add(session);
        return session;
    }

    private void insertUser(UUID id, String nickname) {
        jdbcTemplate.update(""" 
              INSERT INTO users ( id, email, nickname, role, enabled, created_at ) 
              VALUES (?, ?, ?, 'USER', true, CURRENT_TIMESTAMP) 
              """, id, id + "@test.example", nickname);
    }

    private void insertConversation(
            UUID id,
            UUID firstUserId,
            UUID secondUserId
    ) {
        boolean firstUserComesFirst = firstUserId.toString().compareTo(secondUserId.toString()) < 0;

        UUID userAId = firstUserComesFirst ? firstUserId : secondUserId;
        UUID userBId = firstUserComesFirst ? secondUserId : firstUserId;

        jdbcTemplate.update(""" 
              INSERT INTO conversations ( id, user_a_id, user_b_id, created_at ) 
              VALUES (?, ?, ?, CURRENT_TIMESTAMP)
              """, id, userAId, userBId);
    }

}
