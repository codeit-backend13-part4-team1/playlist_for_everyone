package com.codeit.sb13.playlist_for_everyone.domain.conversation;

import com.codeit.sb13.playlist_for_everyone.PlaylistForEveryoneApplication;
import com.codeit.sb13.playlist_for_everyone.domain.auth.jwt.JwtTokenProvider;
import com.codeit.sb13.playlist_for_everyone.domain.auth.repository.UserSessionRepository;
import com.codeit.sb13.playlist_for_everyone.domain.conversation.dto.command.DirectMessageReadCommand;
import com.codeit.sb13.playlist_for_everyone.domain.conversation.dto.command.DirectMessageSendCommand;
import com.codeit.sb13.playlist_for_everyone.domain.conversation.dto.response.MessageDto;
import com.codeit.sb13.playlist_for_everyone.domain.conversation.event.DirectMessageCreatedEvent;
import com.codeit.sb13.playlist_for_everyone.domain.conversation.service.DirectMessageService;
import com.codeit.sb13.playlist_for_everyone.domain.user.dto.response.UserSummary;
import com.codeit.sb13.playlist_for_everyone.global.exception.sse.SsePublishException;
import com.codeit.sb13.playlist_for_everyone.global.sse.event.SseEvent;
import com.codeit.sb13.playlist_for_everyone.global.sse.event.impl.RedisSseEventPublisher;
import com.codeit.sb13.playlist_for_everyone.global.sse.history.SseHistoryEntry;
import com.codeit.sb13.playlist_for_everyone.global.sse.history.SseHistoryStore;
import com.codeit.sb13.playlist_for_everyone.global.sse.repository.SseEmitterRepository;
import com.codeit.sb13.playlist_for_everyone.global.sse.service.SseConnectionService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.web.server.context.WebServerApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.event.EventListener;
import org.springframework.core.env.Environment;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.messaging.Message;
import org.springframework.messaging.converter.JacksonJsonMessageConverter;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessageType;
import org.springframework.messaging.simp.broker.SimpleBrokerMessageHandler;
import org.springframework.messaging.simp.stomp.StompFrameHandler;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;
import org.springframework.web.socket.sockjs.client.SockJsClient;
import org.springframework.web.socket.sockjs.client.WebSocketTransport;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.lang.reflect.Type;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doCallRealMethod;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * 실제 DM 저장부터 Redis 이력·전송과 HTTP SSE 수신까지 연결한다.
 * 프로필 없는 사용자를 사용하며 서비스·이력 저장소와 기본 전송은 실제 구현을 실행한다.
 * Redis 전송기는 Spy로 감싸고, 전송 실패 복구 사례에서만 해당 발행을 실패시킨다.
 * 두 서버는 같은 JVM의 독립 Spring 컨텍스트이며, 업무 이벤트 기록기는 다른 스레드의 발행도 관찰한다.
 *
 * <p>JWT와 Redis 로그인 세션을 준비해 실제 인증된 HTTP 스트림을 읽는다. WebSocket 동시 구독 검증은
 * STOMP 구독 등록을 확인한 뒤 전송하여 연결 준비와 메시지 전달 사이의 경쟁을 피한다.
 * 재연결은 실제 저장한 DM의 ID를 커서로 사용하며, 복구 DTO와 DB의 읽음 상태를 각각 비교한다.
 * 브라우저 화면·별도 JVM 배포와 모든 장애 상황의 중복 없는 전달을 검증하는 테스트는 아니다.
 */
@SpringBootTest(
        classes = {PlaylistForEveryoneApplication.class, DirectMessageSseIntegrationTest.EventProbeConfig.class},
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "spring.flyway.enabled=true",
                "spring.jpa.hibernate.ddl-auto=validate",
                "spring.jpa.open-in-view=false",
                "spring.docker.compose.enabled=false",
                "everyone.sse.transport=redis",
                "everyone.sse.subscription-enabled=true",
                "everyone.sse.redis.channel=test:sse:dm",
                "everyone.sse.history.key-prefix=test:sse:dm:history",
                "everyone.sse.heartbeat-interval=1h",
                "everyone.sse.connection-timeout=30s",
                "everyone.dm.websocket.transport=redis",
                "everyone.dm.websocket.subscription-enabled=true",
                "everyone.dm.websocket.redis.channel=test:dm:websocket:sse-integration"
        }
)
@Testcontainers
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@DisplayName("DM 저장부터 실제 SSE 수신까지 통합 테스트")
class DirectMessageSseIntegrationTest {

    @Container
    static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16");

    @Container
    static final GenericContainer<?> redis =
            new GenericContainer<>("redis:7.4-alpine").withExposedPorts(6379);

    @DynamicPropertySource
    static void configureInfrastructure(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.datasource.driver-class-name", postgres::getDriverClassName);
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
    }

    @Autowired
    private DirectMessageService directMessageService;
    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private JwtTokenProvider jwtTokenProvider;
    @Autowired
    private UserSessionRepository userSessionRepository;
    @Autowired
    private SseConnectionService sseConnectionService;
    @Autowired
    private SseEmitterRepository emitterRepository;
    @Autowired
    private SseHistoryStore historyStore;
    @Autowired
    private StringRedisTemplate redisTemplate;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private SimpleBrokerMessageHandler simpleBroker;
    @Autowired
    private Environment environment;
    @Autowired
    private EventProbe eventProbe;
    @MockitoSpyBean
    private RedisSseEventPublisher transportPublisher;
    @LocalServerPort
    private int port;

    private final UUID senderId = UUID.randomUUID();
    private final UUID receiverId = UUID.randomUUID();
    private final UUID otherUserId = UUID.randomUUID();
    private final UUID conversationId = UUID.randomUUID();
    private final Map<UUID, UUID> loginSessions = new LinkedHashMap<>();
    private final List<SseClient> sseClients = new ArrayList<>();
    private final List<StompSession> stompSessions = new ArrayList<>();
    private final List<ConfigurableApplicationContext> additionalNodes = new ArrayList<>();
    private WebSocketStompClient stompClient;
    private String senderToken;
    private String receiverToken;
    private String otherToken;

    @BeforeEach
    void setUp() {
        insertUser(senderId, "발신자");
        insertUser(receiverId, "수신자");
        insertUser(otherUserId, "다른 사용자");
        boolean senderFirst = senderId.toString().compareTo(receiverId.toString()) < 0;
        jdbcTemplate.update("""
                INSERT INTO conversations (id, user_a_id, user_b_id, created_at)
                VALUES (?, ?, ?, CURRENT_TIMESTAMP)
                """, conversationId, senderFirst ? senderId : receiverId, senderFirst ? receiverId : senderId);
        senderToken = createToken(senderId);
        receiverToken = createToken(receiverId);
        otherToken = createToken(otherUserId);
        awaitRedisSubscribers(1);
    }

    @AfterEach
    void cleanUp() {
        // 일반 시나리오는 여기서 닫는다. try-with-resources로 먼저 닫으면 읽기 중인 스트림의 잠금을 기다릴 수 있다.
        // 한 정리가 실패해도 나머지 연결·컨텍스트·데이터 정리를 모두 시도한다.
        List<Executable> cleanups = new ArrayList<>();
        cleanups.add(sseConnectionService::closeAllLocalConnections);
        additionalNodes.forEach(node ->
                cleanups.add(() -> node.getBean(SseConnectionService.class).closeAllLocalConnections()));
        sseClients.forEach(client -> cleanups.add(client::close));
        stompSessions.forEach(session -> cleanups.add(() -> {
            if (session.isConnected()) {
                session.disconnect();
            }
        }));
        if (stompClient != null) {
            cleanups.add(stompClient::stop);
        }
        additionalNodes.forEach(node -> cleanups.add(node::close));
        loginSessions.forEach((userId, sessionId) ->
                cleanups.add(() -> userSessionRepository.endIfCurrent(userId, sessionId)));
        cleanups.add(this::deleteTestHistory);
        cleanups.add(() -> jdbcTemplate.update(
                "DELETE FROM direct_messages WHERE conversation_id = ?", conversationId));
        cleanups.add(() -> jdbcTemplate.update("DELETE FROM conversations WHERE id = ?", conversationId));
        cleanups.add(() -> jdbcTemplate.update(
                "DELETE FROM users WHERE id IN (?, ?, ?)", senderId, receiverId, otherUserId));
        assertAll(cleanups);
    }

    @Test
    @DisplayName("실제 저장한 DM을 Redis를 거쳐 이벤트 ID와 DTO가 보존된 SSE로 수신하고 미읽음 상태를 유지한다.")
    void deliversSavedDirectMessageThroughRedisToSse() throws IOException {
        SseClient receiver = openSse(receiverToken);
        MessageDto saved = send("실제 SSE로 전달할 메시지");
        ReadState before = readState(saved.id());

        assertFrame(receiver.readFrame(), saved.id(), messageJson(saved.id()));

        assertThat(before.read()).isFalse();
        assertThat(readState(saved.id())).isEqualTo(before);
        assertMessageIds(saved.id());
        assertHistoryIds(receiverId, saved.id());
        assertCreatedEventOnce(eventProbe, saved.id());
        receiver.assertNoMoreEvents();
    }

    @Test
    @DisplayName("DM은 수신자의 모든 SSE 연결에 한 번씩 전달되고 발신자와 다른 사용자에게는 전달되지 않는다.")
    void deliversOnlyToAllReceiverConnections() throws IOException {
        SseClient receiverOne = openSse(receiverToken);
        SseClient receiverTwo = openSse(receiverToken);
        SseClient sender = openSse(senderToken);
        SseClient other = openSse(otherToken);
        assertThat(emitterRepository.findAllByUserId(receiverId)).hasSize(2);
        MessageDto saved = send("수신자의 여러 탭에 전달할 메시지");
        JsonNode expected = messageJson(saved.id());
        ReadState before = readState(saved.id());

        assertFrame(receiverOne.readFrame(), saved.id(), expected);
        assertFrame(receiverTwo.readFrame(), saved.id(), expected);
        receiverOne.assertNoMoreEvents();
        receiverTwo.assertNoMoreEvents();
        sender.assertNoMoreEvents();
        other.assertNoMoreEvents();

        assertMessageIds(saved.id());
        assertHistoryIds(receiverId, saved.id());
        assertHistoryIds(senderId);
        assertHistoryIds(otherUserId);
        assertThat(before.read()).isFalse();
        assertThat(readState(saved.id())).isEqualTo(before);
    }

    @Test
    @DisplayName("수신자가 WebSocket으로 대화를 구독해도 SSE를 수신하며 DM 저장과 업무 이벤트 및 이력은 중복되지 않는다.")
    void deliversSseWhileReceiverSubscribesToWebSocket() throws Exception {
        BlockingQueue<MessageDto> webSocketMessages = subscribeReceiverToConversation();
        SseClient receiver = openSse(receiverToken);
        MessageDto saved = send("WebSocket과 SSE로 전달할 메시지");
        ReadState before = readState(saved.id());

        assertFrame(receiver.readFrame(), saved.id(), messageJson(saved.id()));
        MessageDto webSocketMessage = webSocketMessages.poll(10, TimeUnit.SECONDS);
        assertThat(webSocketMessage).isNotNull();
        assertThat(webSocketMessage.id()).isEqualTo(saved.id());
        assertThat(webSocketMessage.conversationId()).isEqualTo(conversationId);
        assertThat(webSocketMessage.sender().userId()).isEqualTo(senderId);
        assertThat(webSocketMessage.receiver().userId()).isEqualTo(receiverId);
        assertThat(webSocketMessage.content()).isEqualTo(saved.content());
        receiver.assertNoMoreEvents();
        assertThat(webSocketMessages.poll(1, TimeUnit.SECONDS)).isNull();

        assertMessageIds(saved.id());
        assertHistoryIds(receiverId, saved.id());
        assertCreatedEventOnce(eventProbe, saved.id());
        assertThat(before.read()).isFalse();
        assertThat(readState(saved.id())).isEqualTo(before);
    }

    @Test
    @DisplayName("미접속 중 저장된 DM을 재연결로 순서대로 복구하고 이름이 변경되어도 원래 이벤트 ID와 DTO를 유지한다.")
    void replaysOfflineMessagesWithOriginalSnapshots() throws IOException {
        UUID lastEventId = receiveCursorAndDisconnect();
        MessageDto first = send("미접속 중 첫 번째 메시지");
        MessageDto second = send("미접속 중 두 번째 메시지");
        assertThat(emitterRepository.findAllByUserId(receiverId)).isEmpty();
        List<SseHistoryEntry> snapshots = historyStore.snapshot(receiverId, lastEventId).entries();
        assertThat(snapshots).extracting(entry -> entry.event().eventId())
                .containsExactly(first.id(), second.id());
        assertThat(snapshots.get(0).event().data()).isEqualTo(messageJson(first.id()));
        assertThat(snapshots.get(1).event().data()).isEqualTo(messageJson(second.id()));
        ReadState firstBefore = readState(first.id());
        ReadState secondBefore = readState(second.id());
        jdbcTemplate.update("UPDATE users SET nickname = ? WHERE id = ?", "변경된 발신자", senderId);
        assertThat(persistedMessage(first.id()).sender().name()).isEqualTo("변경된 발신자");

        SseClient receiver = openSse(receiverToken, port, lastEventId);
        assertFrame(receiver.readFrame(), first.id(), snapshots.get(0).event().data());
        assertFrame(receiver.readFrame(), second.id(), snapshots.get(1).event().data());
        receiver.assertNoMoreEvents();

        assertMessageIds(lastEventId, first.id(), second.id());
        assertHistoryIds(receiverId, lastEventId, first.id(), second.id());
        assertThat(firstBefore.read()).isFalse();
        assertThat(secondBefore.read()).isFalse();
        assertThat(readState(first.id())).isEqualTo(firstBefore);
        assertThat(readState(second.id())).isEqualTo(secondBefore);
    }

    @Test
    @DisplayName("이미 읽은 DM도 재연결로 복구하며 읽음 상태와 수정 시각을 변경하지 않는다.")
    void replaysReadMessageWithoutChangingReadState() throws IOException {
        UUID lastEventId = receiveCursorAndDisconnect();
        MessageDto saved = send("이미 읽은 뒤 복구할 메시지");
        JsonNode originalSnapshot = historyStore.snapshot(receiverId, lastEventId)
                .entries().get(0).event().data();
        directMessageService.read(new DirectMessageReadCommand(conversationId, saved.id(), receiverId));
        ReadState before = readState(saved.id());
        assertThat(before.read()).isTrue();
        assertThat(before.updatedAt()).isNotNull();

        SseClient receiver = openSse(receiverToken, port, lastEventId);
        assertFrame(receiver.readFrame(), saved.id(), originalSnapshot);
        receiver.assertNoMoreEvents();

        assertThat(readState(saved.id())).isEqualTo(before);
        assertMessageIds(lastEventId, saved.id());
        assertHistoryIds(receiverId, lastEventId, saved.id());
    }

    @Test
    @DisplayName("서버 A에서 저장한 DM을 B에만 연결된 수신자가 SSE로 받고 중복 저장이나 업무 이벤트 재발행은 하지 않는다.")
    void deliversSavedDirectMessageToReceiverOnAnotherNode() throws IOException {
        // 두 번째 서버의 기동을 끝낸 뒤 SSE를 열어 기동 시간이 연결 제한 시간에 포함되지 않게 한다.
        ConfigurableApplicationContext nodeB = startAdditionalNode();
        int portB = ((WebServerApplicationContext) nodeB).getWebServer().getPort();
        SseEmitterRepository emittersB = nodeB.getBean(SseEmitterRepository.class);
        assertThat(portB).isNotEqualTo(port);
        assertThat(emittersB).isNotSameAs(emitterRepository);
        awaitRedisSubscribers(2);

        SseClient receiver = openSse(receiverToken, portB, null);
        assertThat(emitterRepository.findAllByUserId(receiverId)).isEmpty();
        assertThat(emittersB.findAllByUserId(receiverId)).hasSize(1);
        MessageDto saved = send("A에서 B로 전달할 DM");
        ReadState before = readState(saved.id());

        assertFrame(receiver.readFrame(), saved.id(), messageJson(saved.id()));
        receiver.assertNoMoreEvents();

        assertMessageIds(saved.id());
        assertHistoryIds(receiverId, saved.id());
        assertCreatedEventOnce(eventProbe, saved.id());
        assertThat(nodeB.getBean(EventProbe.class).eventsFor(conversationId)).isEmpty();
        assertThat(before.read()).isFalse();
        assertThat(readState(saved.id())).isEqualTo(before);
    }

    @ParameterizedTest(name = "재연결 전 읽음 상태: {0}")
    @ValueSource(booleans = {false, true})
    @DisplayName("이력 저장 후 Redis 발행이 실패해도 같은 DM을 한 번 복구하고 읽음 상태와 수정 시각을 유지한다.")
    void replaysStoredHistoryAfterTransportFailure(boolean readBeforeReconnect) throws IOException {
        UUID cursor = receiveCursorAndDisconnect();
        SsePublishException failure = new SsePublishException(new IllegalStateException("테스트용 Redis 전송 실패"));
        MessageDto saved;
        doThrow(failure).when(transportPublisher).publish(any(SseEvent.class));
        try {
            // 공통 HistorySavingSseEventPublisher와 Redis 이력 저장은 실제로 실행한다.
            saved = send("이력은 저장됐지만 실시간 전송은 실패한 메시지");
        } finally {
            doCallRealMethod().when(transportPublisher).publish(any(SseEvent.class));
        }

        List<SseHistoryEntry> snapshots = historyStore.snapshot(receiverId, cursor).entries();
        assertThat(snapshots).hasSize(1);
        SseEvent<?> storedEvent = snapshots.get(0).event();
        assertThat(storedEvent.eventId()).isEqualTo(saved.id());
        assertThat(storedEvent.data()).isEqualTo(messageJson(saved.id()));
        verify(transportPublisher, times(1)).publish(storedEvent);
        assertThat(emitterRepository.findAllByUserId(receiverId)).isEmpty();

        if (readBeforeReconnect) {
            directMessageService.read(new DirectMessageReadCommand(conversationId, saved.id(), receiverId));
        }
        ReadState before = readState(saved.id());
        assertThat(before.read()).isEqualTo(readBeforeReconnect);
        SseClient receiver = openSse(receiverToken, port, cursor);
        assertFrame(receiver.readFrame(), saved.id(), snapshots.get(0).event().data());
        receiver.assertNoMoreEvents();

        assertMessageIds(cursor, saved.id());
        assertHistoryIds(receiverId, cursor, saved.id());
        assertThat(eventProbe.eventsFor(conversationId)).extracting(DirectMessageCreatedEvent::directMessageId)
                .containsExactly(cursor, saved.id());
        assertThat(readState(saved.id())).isEqualTo(before);
        // 재연결은 이력을 읽을 뿐, 실패한 Redis 신호를 자동으로 재발행하지 않는다.
        verify(transportPublisher, times(1)).publish(storedEvent);
    }

    private MessageDto send(String content) {
        return directMessageService.send(new DirectMessageSendCommand(conversationId, senderId, content));
    }

    private void assertMessageIds(UUID... ids) {
        assertThat(jdbcTemplate.queryForList(
                "SELECT id FROM direct_messages WHERE conversation_id = ?", UUID.class, conversationId))
                .containsExactlyInAnyOrder(ids);
    }

    private void assertHistoryIds(UUID userId, UUID... ids) {
        assertThat(historyStore.readAfter(userId, null).entries())
                .extracting(entry -> entry.event().eventId()).containsExactly(ids);
    }

    private void assertCreatedEventOnce(EventProbe probe, UUID messageId) {
        assertThat(probe.eventsFor(conversationId)).containsExactly(
                new DirectMessageCreatedEvent(messageId, conversationId, senderId, "발신자", receiverId));
    }

    private void assertFrame(SseFrame frame, UUID messageId, JsonNode expectedData) {
        assertThat(frame.id()).isEqualTo(messageId);
        assertThat(frame.name()).isEqualTo("direct-messages");
        assertThat(frame.data()).isEqualTo(expectedData);
    }

    private JsonNode messageJson(UUID messageId) {
        // 저장 전 엔티티의 나노초 대신 DB에 실제 저장된 생성 시각을 기준으로 비교한다.
        return objectMapper.valueToTree(persistedMessage(messageId));
    }

    private MessageDto persistedMessage(UUID messageId) {
        return jdbcTemplate.queryForObject("""
                SELECT dm.id, dm.conversation_id, dm.created_at, dm.content,
                       dm.sender_id, sender.nickname AS sender_name,
                       dm.receiver_id, receiver.nickname AS receiver_name
                FROM direct_messages dm
                JOIN users sender ON sender.id = dm.sender_id
                JOIN users receiver ON receiver.id = dm.receiver_id
                WHERE dm.id = ? AND dm.conversation_id = ?
                """, (row, number) -> new MessageDto(
                row.getObject("id", UUID.class), row.getObject("conversation_id", UUID.class),
                row.getTimestamp("created_at").toLocalDateTime(),
                new UserSummary(row.getObject("sender_id", UUID.class), row.getString("sender_name"), null),
                new UserSummary(row.getObject("receiver_id", UUID.class), row.getString("receiver_name"), null),
                row.getString("content")), messageId, conversationId);
    }

    private ReadState readState(UUID messageId) {
        return jdbcTemplate.queryForObject("""
                SELECT is_read, updated_at FROM direct_messages
                WHERE id = ? AND conversation_id = ?
                """, (row, number) -> new ReadState(
                row.getBoolean("is_read"), row.getTimestamp("updated_at").toLocalDateTime()),
                messageId, conversationId);
    }

    private void insertUser(UUID id, String name) {
        jdbcTemplate.update("""
                INSERT INTO users (id, email, nickname, role, enabled, created_at)
                VALUES (?, ?, ?, 'USER', true, CURRENT_TIMESTAMP)
                """, id, id + "@test.example", name);
    }

    private String createToken(UUID userId) {
        UUID sessionId = userSessionRepository.start(userId, Duration.ofHours(1)).sessionId();
        loginSessions.put(userId, sessionId);
        return jwtTokenProvider.createAccessToken(userId, sessionId);
    }

    private UUID receiveCursorAndDisconnect() throws IOException {
        UUID cursor;
        try (SseClient receiver = openSse(receiverToken)) {
            MessageDto saved = send("재연결 기준 메시지");
            assertFrame(receiver.readFrame(), saved.id(), messageJson(saved.id()));
            cursor = saved.id();
            // HTTP 클라이언트 종료 감지에 의존하지 않고 서버 측 연결 정리까지 완료한다.
            sseConnectionService.closeAllLocalConnections();
        }
        await().atMost(Duration.ofSeconds(5))
                .untilAsserted(() -> assertThat(emitterRepository.findAllByUserId(receiverId)).isEmpty());
        return cursor;
    }

    private SseClient openSse(String token) throws IOException {
        return openSse(token, port, null);
    }

    private SseClient openSse(String token, int targetPort, UUID lastEventId) throws IOException {
        String address = "http://localhost:" + targetPort + "/api/sse";
        if (lastEventId != null) {
            address += "?lastEventId=" + lastEventId;
        }
        HttpURLConnection connection = (HttpURLConnection) URI.create(address).toURL().openConnection();
        connection.setRequestMethod("GET");
        connection.setRequestProperty(HttpHeaders.AUTHORIZATION, "Bearer " + token);
        connection.setRequestProperty(HttpHeaders.ACCEPT, MediaType.TEXT_EVENT_STREAM_VALUE);
        connection.setConnectTimeout(5_000);
        connection.setReadTimeout(10_000);
        connection.setInstanceFollowRedirects(false);
        try {
            assertThat(connection.getResponseCode()).isEqualTo(200);
            assertThat(MediaType.parseMediaType(connection.getContentType())
                    .isCompatibleWith(MediaType.TEXT_EVENT_STREAM)).isTrue();
            SseClient client = new SseClient(connection);
            sseClients.add(client);
            client.readInitialComment();
            client.startReading();
            return client;
        } catch (IOException | RuntimeException | Error failure) {
            connection.disconnect();
            throw failure;
        }
    }

    /** 구독 준비 완료를 기다리는 용도다. 실제 수신 성공은 이후 HTTP·STOMP 프레임으로 따로 확인한다. */
    private void awaitRedisSubscribers(int expectedCount) {
        String sseChannel = environment.getRequiredProperty("everyone.sse.redis.channel");
        String dmChannel = environment.getRequiredProperty("everyone.dm.websocket.redis.channel");
        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
            var result = redis.execInContainer("redis-cli", "--raw", "PUBSUB", "NUMSUB", sseChannel, dmChannel);
            assertThat(result.getExitCode()).isZero();
            assertThat(result.getStdout().lines().map(String::strip).toList())
                    .containsExactly(sseChannel, Integer.toString(expectedCount),
                            dmChannel, Integer.toString(expectedCount));
        });
    }

    private BlockingQueue<MessageDto> subscribeReceiverToConversation() throws Exception {
        stompClient = new WebSocketStompClient(new SockJsClient(
                List.of(new WebSocketTransport(new StandardWebSocketClient()))));
        stompClient.setMessageConverter(new JacksonJsonMessageConverter());
        stompClient.start();
        StompHeaders connectHeaders = new StompHeaders();
        connectHeaders.add(HttpHeaders.AUTHORIZATION, "Bearer " + receiverToken);
        WebSocketHttpHeaders handshakeHeaders = new WebSocketHttpHeaders();
        handshakeHeaders.setOrigin("http://localhost:" + port);
        StompSession session = stompClient.connectAsync("http://localhost:" + port + "/ws",
                handshakeHeaders, connectHeaders, new StompSessionHandlerAdapter() {})
                .get(10, TimeUnit.SECONDS);
        stompSessions.add(session);
        BlockingQueue<MessageDto> messages = new LinkedBlockingQueue<>();
        String destination = "/sub/conversations/" + conversationId + "/direct-messages";
        String subscriptionId = UUID.randomUUID().toString();
        StompHeaders headers = new StompHeaders();
        headers.setDestination(destination);
        headers.setId(subscriptionId);
        session.subscribe(headers, new StompFrameHandler() {
            @Override
            public Type getPayloadType(StompHeaders frameHeaders) {
                return MessageDto.class;
            }

            @Override
            public void handleFrame(StompHeaders frameHeaders, Object payload) {
                messages.add((MessageDto) payload);
            }
        });
        SimpMessageHeaderAccessor lookup = SimpMessageHeaderAccessor.create(SimpMessageType.MESSAGE);
        lookup.setDestination(destination);
        Message<byte[]> lookupMessage = MessageBuilder.createMessage(new byte[0], lookup.getMessageHeaders());
        await().atMost(Duration.ofSeconds(5)).until(() -> simpleBroker.getSubscriptionRegistry()
                .findSubscriptions(lookupMessage).values().stream().anyMatch(ids -> ids.contains(subscriptionId)));
        return messages;
    }

    /**
     * 같은 DB·Redis·JWT 설정을 공유하되 HTTP 포트와 로컬 연결 저장소가 독립적인 두 번째 서버를 만든다.
     * 서버 B에만 연결된 사용자가 A의 DM을 받는지 확인하며, 별도 프로세스나 Compose 앱을 띄우지는 않는다.
     */
    private ConfigurableApplicationContext startAdditionalNode() {
        ConfigurableApplicationContext node = new SpringApplicationBuilder(
                PlaylistForEveryoneApplication.class, EventProbeConfig.class)
                .registerShutdownHook(false)
                .run(
                        "--spring.profiles.active=test",
                        "--server.port=0",
                        "--spring.application.name=dm-sse-node-b-test",
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
                        "--everyone.sse.transport=redis",
                        "--everyone.sse.subscription-enabled=true",
                        "--everyone.sse.redis.channel=" + environment.getRequiredProperty("everyone.sse.redis.channel"),
                        "--everyone.sse.history.key-prefix=" + environment.getRequiredProperty("everyone.sse.history.key-prefix"),
                        "--everyone.sse.heartbeat-interval=1h",
                        "--everyone.sse.connection-timeout=30s",
                        "--everyone.dm.websocket.transport=redis",
                        "--everyone.dm.websocket.subscription-enabled=true",
                        "--everyone.dm.websocket.redis.channel=" + environment.getRequiredProperty("everyone.dm.websocket.redis.channel")
                );
        additionalNodes.add(node);
        return node;
    }

    private void deleteTestHistory() {
        String prefix = environment.getRequiredProperty("everyone.sse.history.key-prefix");
        for (UUID userId : List.of(senderId, receiverId, otherUserId)) {
            redisTemplate.delete(List.of(
                    "%s:{%s}:stream".formatted(prefix, userId),
                    "%s:{%s}:event-ids".formatted(prefix, userId)));
        }
    }

    private record ReadState(boolean read, LocalDateTime updatedAt) {}

    private record SseFrame(UUID id, String name, JsonNode data) {}

    private record SseReadResult(SseFrame frame, Throwable failure) {}

    /**
     * 연결별 읽기 스레드가 SSE 프레임과 수신 오류를 모두 큐에 전달하는 테스트 클라이언트다.
     * 읽기 실패를 이벤트 미수신으로 오인하지 않도록 테스트 스레드에서 오류도 검증한다.
     * 초기 연결 주석과 현재 서버의 id/event/data 프레임 형식을 읽으며 범용 SSE 파서는 아니다.
     */
    private class SseClient implements AutoCloseable {
        private final HttpURLConnection connection;
        private final BufferedReader reader;
        private final BlockingQueue<SseReadResult> received = new LinkedBlockingQueue<>();
        private final ExecutorService readerExecutor = Executors.newSingleThreadExecutor();
        private volatile boolean closed;

        private SseClient(HttpURLConnection connection) throws IOException {
            this.connection = connection;
            this.reader = new BufferedReader(new InputStreamReader(
                    connection.getInputStream(), StandardCharsets.UTF_8));
        }

        private void readInitialComment() throws IOException {
            assertThat(reader.readLine()).isEqualTo(":connected");
            assertThat(reader.readLine()).isEmpty();
        }

        private void startReading() {
            readerExecutor.submit(() -> {
                try {
                    while (!closed) {
                        received.add(new SseReadResult(readWireFrame(), null));
                    }
                } catch (IOException | RuntimeException | AssertionError failure) {
                    if (!closed) {
                        received.add(new SseReadResult(null, failure));
                    }
                }
            });
        }

        private SseFrame readWireFrame() throws IOException {
            String idLine = reader.readLine();
            String nameLine = reader.readLine();
            String dataLine = reader.readLine();
            assertThat(idLine).startsWith("id:");
            assertThat(nameLine).startsWith("event:");
            assertThat(dataLine).startsWith("data:");
            assertThat(reader.readLine()).isEmpty();
            return new SseFrame(UUID.fromString(idLine.substring(3)), nameLine.substring(6),
                    objectMapper.readTree(dataLine.substring(5)));
        }

        private SseFrame readFrame() throws IOException {
            SseReadResult result = poll(10);
            assertThat(result).as("제한 시간 내 SSE 프레임 수신").isNotNull();
            if (result.failure() != null) {
                throw new AssertionError("SSE 프레임 수신 실패", result.failure());
            }
            return result.frame();
        }

        private void assertNoMoreEvents() throws IOException {
            // 연결마다 독립적으로 읽어, 여러 연결의 미전달 검증이 소켓 제한 시간만큼 누적되지 않게 한다.
            // EOF·파싱 실패도 큐로 전달되므로 연결 종료를 정상적인 미전달로 취급하지 않는다.
            // 1초 관찰 구간의 추가 전달 여부만 검증하며 이후의 모든 중복 가능성을 배제하지는 않는다.
            assertThat(poll(1)).as("추가 SSE 프레임 또는 연결 오류가 없어야 한다").isNull();
        }

        private SseReadResult poll(long seconds) throws IOException {
            try {
                return received.poll(seconds, TimeUnit.SECONDS);
            } catch (InterruptedException failure) {
                Thread.currentThread().interrupt();
                throw new IOException("SSE 수신 대기 중 인터럽트", failure);
            }
        }

        @Override
        public void close() throws IOException {
            if (!closed) {
                closed = true;
                try {
                    connection.disconnect();
                } finally {
                    readerExecutor.shutdownNow();
                    reader.close();
                }
                try {
                    if (!readerExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                        throw new IOException("SSE 테스트 수신 스레드가 종료되지 않았습니다.");
                    }
                } catch (InterruptedException failure) {
                    Thread.currentThread().interrupt();
                    throw new IOException("SSE 테스트 수신 스레드 종료 중 인터럽트", failure);
                }
            }
        }
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class EventProbeConfig {
        @Bean
        EventProbe directMessageEventProbe() {
            return new EventProbe();
        }
    }

    /** 요청 스레드와 Redis 수신 스레드의 이벤트를 함께 모아 서버 B의 업무 이벤트 재발행 여부를 확인한다. */
    static class EventProbe {
        private final ConcurrentLinkedQueue<DirectMessageCreatedEvent> events = new ConcurrentLinkedQueue<>();

        @EventListener
        public void onCreated(DirectMessageCreatedEvent event) {
            events.add(event);
        }

        List<DirectMessageCreatedEvent> eventsFor(UUID conversationId) {
            return events.stream().filter(event -> event.conversationId().equals(conversationId)).toList();
        }
    }
}
