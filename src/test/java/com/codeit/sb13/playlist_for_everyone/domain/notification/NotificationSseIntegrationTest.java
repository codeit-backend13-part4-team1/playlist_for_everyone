package com.codeit.sb13.playlist_for_everyone.domain.notification;

import com.codeit.sb13.playlist_for_everyone.PlaylistForEveryoneApplication;
import com.codeit.sb13.playlist_for_everyone.domain.auth.jwt.JwtTokenProvider;
import com.codeit.sb13.playlist_for_everyone.domain.notification.dto.event.NotificationCreateRequestedEvent;
import com.codeit.sb13.playlist_for_everyone.domain.notification.dto.response.NotificationDto;
import com.codeit.sb13.playlist_for_everyone.domain.notification.entity.Notification;
import com.codeit.sb13.playlist_for_everyone.domain.notification.entity.NotificationLevel;
import com.codeit.sb13.playlist_for_everyone.domain.notification.event.NotificationCreatedEvent;
import com.codeit.sb13.playlist_for_everyone.domain.notification.repository.NotificationRepository;
import com.codeit.sb13.playlist_for_everyone.global.sse.config.SseHistoryProperties;
import com.codeit.sb13.playlist_for_everyone.global.sse.event.Connection;
import com.codeit.sb13.playlist_for_everyone.global.sse.event.SseEvent;
import com.codeit.sb13.playlist_for_everyone.global.sse.event.SseEventPublisher;
import com.codeit.sb13.playlist_for_everyone.global.sse.history.SseHistoryReadResult;
import com.codeit.sb13.playlist_for_everyone.global.sse.history.SseHistorySnapshot;
import com.codeit.sb13.playlist_for_everyone.global.sse.history.SseHistoryStore;
import com.codeit.sb13.playlist_for_everyone.global.sse.repository.SseEmitterRepository;
import com.codeit.sb13.playlist_for_everyone.global.sse.service.SseConnectionService;
import com.codeit.sb13.playlist_for_everyone.global.sse.service.SseDeliveryService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.web.server.context.WebServerApplicationContext;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;
import tools.jackson.databind.ObjectMapper;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.SocketTimeoutException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * 실제 PostgreSQL·Redis·HTTP 서버로 공통 알림 생성 요청부터 SSE 프레임 수신까지 검증한다.
 *
 * <p>DB에 사용자를 준비하고 실제 JwtTokenProvider로 발급한 토큰을 사용한다. JWT 필터와
 * 사용자 조회는 실제로 실행하지만, 회원가입·로그인 API를 통한 토큰 발급 과정은 검증하지 않는다.
 * 서버 간 사례는 동일 JVM의 독립 Spring 컨텍스트 두 개를 사용하며 별도 OS 프로세스는 아니다.
 * 공유 이력 복구·알림 삭제 필터·연결별 커서·전송 순서·한 연결 종료·전체 이력 만료도 실제 HTTP로 확인한다.
 * 짧은 키 TTL은 테스트에서 만료 상태를 만드는 용도이며 운영 보관기간을 검증하는 수치는 아니다.
 */
@RecordApplicationEvents
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "spring.flyway.enabled=true",
                "spring.jpa.hibernate.ddl-auto=validate",
                "spring.docker.compose.enabled=false",
                "everyone.sse.transport=redis",
                // 테스트 프로필의 자동 구독 비활성화를 이 클래스에서만 덮어쓴다.
                "everyone.sse.subscription-enabled=true",
                "everyone.sse.redis.channel=test:sse:notification",
                // 테스트의 초기 주석·이벤트 프레임 판독에 주기적 하트비트가 끼어들지 않게 한다.
                "everyone.sse.heartbeat-interval=1h",
                "everyone.sse.connection-timeout=30s"
        }
)
@Testcontainers
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@DisplayName("알림 저장부터 실제 SSE 수신까지 통합 테스트")
public class NotificationSseIntegrationTest {

    @Container
    static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16");

    @Container
    static final GenericContainer<?> redis = new GenericContainer<>(DockerImageName.parse("redis:7.4-alpine"))
            .withExposedPorts(6379);

    @DynamicPropertySource
    static void configureInfrastructure(DynamicPropertyRegistry registry){
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.datasource.driver-class-name", ()-> "org.postgresql.Driver");
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", () -> redis.getMappedPort(6379));

    }


    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @Autowired
    private SseConnectionService sseConnectionService;

    @Autowired
    private ApplicationEventPublisher eventPublisher;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private NotificationRepository notificationRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private SseEmitterRepository sseEmitterRepository;

    @MockitoSpyBean
    private SseHistoryStore historyStore;

    @MockitoSpyBean
    private SseDeliveryService sseDeliveryService;

    @Autowired
    private SseEventPublisher sseEventPublisher;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Autowired
    private SseHistoryProperties historyProperties;

    @MockitoSpyBean(name = "sseRetryTaskScheduler")
    private TaskScheduler retryTaskScheduler;

    @MockitoSpyBean
    @Qualifier("sseTransportPublisher")
    private SseEventPublisher transportPublisher;

    @LocalServerPort
    private int port;

    private String accessToken;

    private String otherAccessToken;

    private TransactionTemplate transactionTemplate;

    private static final UUID RECEIVER_ID = UUID.randomUUID();

    private static final UUID OTHER_USER_ID = UUID.randomUUID();

    @BeforeEach
    void setUp(){
        // HTTP 인증과 별도 알림 저장 트랜잭션 모두에서 조회할 수 있게 사용자를 먼저 저장한다.
        insertUser(RECEIVER_ID, "알림 수신자");
        insertUser(OTHER_USER_ID, "다른 사용자");

        accessToken = jwtTokenProvider.createAccessToken(RECEIVER_ID);
        otherAccessToken = jwtTokenProvider.createAccessToken(OTHER_USER_ID);
        transactionTemplate = new TransactionTemplate(transactionManager);
    }


    @AfterEach
    void cleanUp(){
        // 실제로 커밋한 데이터이므로 테스트 기본 롤백에 맡기지 않는다. FK 자식부터 삭제한다.
        jdbcTemplate.update("DELETE FROM notifications WHERE receiver_id IN(?,?) ", RECEIVER_ID, OTHER_USER_ID);
        jdbcTemplate.update("DELETE FROM users WHERE id IN (?, ?)", RECEIVER_ID,OTHER_USER_ID);
    }

    @Test
    @DisplayName("DB에 저장한 수신자의 JWT로 SSE에 연결하여 최초 연결 주석을 받는다")
    void connectsWithReceiverTokenAndReceivesInitialComment() throws IOException {
        HttpURLConnection connection = createSseConnection(accessToken);
        try {
            assertThat(connection.getResponseCode()).isEqualTo(200);
            MediaType contentType = MediaType.parseMediaType(connection.getContentType());

            assertThat(contentType.isCompatibleWith(MediaType.TEXT_EVENT_STREAM)).isTrue();

            try (BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8))){
                assertThat(reader.readLine()).isEqualTo(":connected");
                assertThat(reader.readLine()).isEmpty();
            }
        }finally {
            try {
                sseConnectionService.closeAllLocalConnections();
            } finally {
                connection.disconnect();
            }
        }
    }

    @Test
    @DisplayName("저장된 알림이 Redis를 거쳐 이벤트ID와 DTO를 유지한 SSE 응답으로 전달된다.")
    void deliversSavedNotificationThroughRedisToSse(ApplicationEvents applicationEvents) throws IOException {
        HttpURLConnection connection = createSseConnection(accessToken);

        try {
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8))){
                assertThat(reader.readLine()).isEqualTo(":connected");
                assertThat(reader.readLine()).isEmpty();

                publishAndCommit(new NotificationCreateRequestedEvent(RECEIVER_ID, "새 알림", "알림 내용", NotificationLevel.INFO));

                List<NotificationCreatedEvent> createdEvents = applicationEvents.stream(NotificationCreatedEvent.class).toList();

                assertThat(createdEvents).hasSize(1);
                NotificationCreatedEvent createdEvent = createdEvents.get(0);

                assertThat(notificationRepository.findAll())
                        .extracting(Notification::getId)
                        .containsExactly(createdEvent.notificationDto().id());

                assertThat(reader.readLine())
                        .isEqualTo("id:" + createdEvent.eventId());

                assertThat(reader.readLine())
                        .isEqualTo("event:notifications");

                String dataLine = reader.readLine();
                assertThat(dataLine).startsWith("data:");
                assertThat(objectMapper.readTree(dataLine.substring("data:".length())))
                        .isEqualTo(objectMapper.valueToTree(createdEvent.notificationDto()));

                assertThat(reader.readLine()).isEmpty();
            }
        }finally {
            try {
                sseConnectionService.closeAllLocalConnections();
            }finally {
                connection.disconnect();
            }
        }
    }

    @Test
    @DisplayName("알림은 지정된 수신자의 SSE 연결에만 전달되고 다른 사용자에게는 전달되지 않는다.")
    void doesNotDeliverNotificationToOtherUser(ApplicationEvents applicationEvents) throws IOException {
        HttpURLConnection receiverConnection = createSseConnection(accessToken);
        HttpURLConnection otherConnection = createSseConnection(otherAccessToken);

        try {
            assertThat(receiverConnection.getResponseCode()).isEqualTo(200);
            assertThat(otherConnection.getResponseCode()).isEqualTo(200);

            try (BufferedReader receiverReader = new BufferedReader(new InputStreamReader(receiverConnection.getInputStream(), StandardCharsets.UTF_8));
                BufferedReader otherReader = new BufferedReader(new InputStreamReader(otherConnection.getInputStream(), StandardCharsets.UTF_8));
            ){
                assertThat(receiverReader.readLine()).isEqualTo(":connected");
                assertThat(receiverReader.readLine()).isEmpty();

                assertThat(otherReader.readLine()).isEqualTo(":connected");
                assertThat(otherReader.readLine()).isEmpty();

                publishAndCommit(new NotificationCreateRequestedEvent(
                        RECEIVER_ID,
                        "수신자 전용 알림",
                        "지정된 사용자에게만 전달할 내용",
                        NotificationLevel.INFO)
                );

                List<NotificationCreatedEvent> createdEvents = applicationEvents.stream(NotificationCreatedEvent.class).toList();

                assertThat(createdEvents).hasSize(1);
                NotificationCreatedEvent createdEvent = createdEvents.get(0);

                assertThat(receiverReader.readLine()).isEqualTo("id:"+ createdEvent.eventId());
                assertThat(receiverReader.readLine()).isEqualTo("event:notifications");
                assertThat(receiverReader.readLine()).startsWith("data:");
                assertThat(receiverReader.readLine()).isEmpty();

                // 다른 연결에서는 읽기 제한 시간 동안 데이터가 오지 않음을 확인한다.
                // 모든 시간대의 미전달이나 장애 상황의 전달 보장을 증명하는 검증은 아니다.
                assertThatThrownBy(()-> otherReader.readLine())
                        .isInstanceOf(SocketTimeoutException.class);

            }
        } finally {
            try {
                sseConnectionService.closeAllLocalConnections();
            } finally {
                receiverConnection.disconnect();
                otherConnection.disconnect();
            }
        }

    }

    @Test
    @DisplayName("A 서버에서 저장한 알림이 Redis를 거쳐 B 서버에서만 연결된 수신자에게 전달된다.")
    void deliversSavedNotificationToReceiverOnAnotherNode(ApplicationEvents applicationEvents) throws IOException {
        try (ConfigurableApplicationContext contextB = startAdditionalNode()){
            WebServerApplicationContext webContextB = (WebServerApplicationContext) contextB;

            int portOnB = webContextB.getWebServer().getPort();
            assertThat(portOnB).isPositive().isNotEqualTo(port);
            SseConnectionService connectionServiceOnB = contextB.getBean(SseConnectionService.class);

            HttpURLConnection connection = createSseConnection(accessToken, portOnB);

            try {
                assertThat(connection.getResponseCode()).isEqualTo(200);
                MediaType contentType = MediaType.parseMediaType(connection.getContentType());

                assertThat(contentType.isCompatibleWith(MediaType.TEXT_EVENT_STREAM)).isTrue();

                try (BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8))){
                    assertThat(reader.readLine()).isEqualTo(":connected");
                    assertThat(reader.readLine()).isEmpty();

                    // 발행 서버 A에는 연결이 없어도 B의 연결로 전달되어야 한다.
                    assertThat(sseEmitterRepository.findAllByUserId(RECEIVER_ID)).isEmpty();

                    publishAndCommit(new NotificationCreateRequestedEvent(
                            RECEIVER_ID,
                            "서버 간 알림",
                            "A에서 저장하고 B에서 수신하는 알림",
                            NotificationLevel.INFO)
                    );

                    List<NotificationCreatedEvent> createdEvents = applicationEvents
                            .stream(NotificationCreatedEvent.class).toList();

                    assertThat(createdEvents).hasSize(1);
                    NotificationCreatedEvent createdEvent = createdEvents.get(0);

                    assertThat(notificationRepository.findAll())
                            .extracting(Notification::getId)
                            .containsExactly(createdEvent.notificationDto().id());

                    assertThat(reader.readLine())
                            .isEqualTo("id:"+ createdEvent.eventId());
                    assertThat(reader.readLine())
                            .isEqualTo("event:notifications");

                    String dataLine = reader.readLine();
                    assertThat(dataLine).startsWith("data:");
                    assertThat(objectMapper.readTree(dataLine.substring("data:".length())))
                            .isEqualTo(objectMapper.valueToTree(createdEvent.notificationDto()));

                    assertThat(reader.readLine()).isEmpty();
                }
            }finally {
                try {
                    // emitter를 가진 B에서 정리한다. 이후 클라이언트와 B 컨텍스트도 닫는다.
                    connectionServiceOnB.closeAllLocalConnections();
                }finally {
                    connection.disconnect();
                }
            }
        }
    }

    @Test
    @DisplayName("재연결하면 Redis 이력 중 DB에서 삭제된 아림을 제외하고 남은 알림만 전달한다.")
    void replayOnlyExistingNotificationsOnReconnect(ApplicationEvents applicationEvents) throws IOException {
        for (String title: List.of("기준 알림", "삭제할 알림", "남길 알림")){
            publishAndCommit(new NotificationCreateRequestedEvent(
                    RECEIVER_ID, title, "재연결 복구 테스트", NotificationLevel.INFO)
            );
        }

        List<NotificationCreatedEvent> events = applicationEvents
                .stream(NotificationCreatedEvent.class)
                .toList();

        assertThat(events).hasSize(3);
        NotificationCreatedEvent cursorEvent = events.get(0);
        NotificationCreatedEvent deletedEvent = events.get(1);
        NotificationCreatedEvent retainedEvent = events.get(2);

        notificationRepository.deleteById(deletedEvent.notificationDto().id());

        assertThat(notificationRepository.existsById(deletedEvent.notificationDto().id()))
                .isFalse();

        assertThat(historyStore.snapshot(RECEIVER_ID, cursorEvent.eventId()).entries())
                .extracting(entry -> entry.event().eventId())
                .containsExactly(deletedEvent.eventId(), retainedEvent.eventId());

        HttpURLConnection connection = createSseConnection(accessToken, port, cursorEvent.eventId());

        try {
            assertThat(connection.getResponseCode()).isEqualTo(200);
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8))){
                assertThat(reader.readLine()).isEqualTo(":connected");
                assertThat(reader.readLine()).isEmpty();

                assertNotificationFrame(reader, retainedEvent);

                assertThatThrownBy(() -> reader.readLine())
                        .isInstanceOf(SocketTimeoutException.class);
            }
        } finally {
            try {
                sseConnectionService.closeAllLocalConnections();
            } finally {
                connection.disconnect();
            }
        }
    }


    @Test
    @DisplayName("재연결 기준 알림이 DB에서 삭제되어도 Redis에 남은 기준 위치 이후 알림을 복구한다.")
    void recoversAfterDeletedCursorNotification(ApplicationEvents applicationEvents) throws IOException {
        for (String title : List.of("삭제할 기준 알림", "복구할 알림")) {
            publishAndCommit(new NotificationCreateRequestedEvent(
                    RECEIVER_ID, title, "기준 알림 삭제 후 복구 테스트", NotificationLevel.INFO
            ));
        }

        List<NotificationCreatedEvent> events = applicationEvents
                .stream(NotificationCreatedEvent.class)
                .toList();

        assertThat(events).hasSize(2);
        NotificationCreatedEvent cursorEvent = events.get(0);
        NotificationCreatedEvent retainedEvent = events.get(1);

        notificationRepository.deleteById(cursorEvent.notificationDto().id());

        assertThat(notificationRepository.existsById(cursorEvent.notificationDto().id()))
                .isFalse();

        assertThat(historyStore.snapshot(RECEIVER_ID, cursorEvent.eventId()).entries())
                .extracting(entry -> entry.event().eventId())
                .containsExactly(retainedEvent.eventId());

        HttpURLConnection connection = createSseConnection(accessToken, port, cursorEvent.eventId());

        try {
            assertThat(connection.getResponseCode()).isEqualTo(200);
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8))) {
                assertThat(reader.readLine()).isEqualTo(":connected");
                assertThat(reader.readLine()).isEmpty();

                assertNotificationFrame(reader, retainedEvent);

                assertThatThrownBy(() -> reader.readLine())
                        .isInstanceOf(SocketTimeoutException.class);
            }
        } finally {
            try {
                sseConnectionService.closeAllLocalConnections();
            } finally {
                connection.disconnect();
            }
        }
    }

    @Test
    @DisplayName("A 서버에서 연결을 끊은 동안 발생한 알림을 B 서버 재연결 시 순서대로 복구한다.")
    void replaysMissedNotificationsAfterReconnectToAnotherNode(
            ApplicationEvents applicationEvents
    ) throws IOException {
        NotificationCreatedEvent cursorEvent;
        HttpURLConnection connectionOnA = createSseConnection(accessToken);

        try {
            assertThat(connectionOnA.getResponseCode()).isEqualTo(200);

            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(connectionOnA.getInputStream(), StandardCharsets.UTF_8))) {
                assertThat(reader.readLine()).isEqualTo(":connected");
                assertThat(reader.readLine()).isEmpty();

                publishAndCommit(new NotificationCreateRequestedEvent(
                        RECEIVER_ID,
                        "기준 알림",
                        "A에서 수신하는 알림",
                        NotificationLevel.INFO
                ));

                List<NotificationCreatedEvent> events = applicationEvents
                        .stream(NotificationCreatedEvent.class)
                        .toList();

                assertThat(events).hasSize(1);
                cursorEvent = events.get(0);
                assertNotificationFrame(reader, cursorEvent);
            }
        } finally {
            try {
                sseConnectionService.closeAllLocalConnections();
            } finally {
                connectionOnA.disconnect();
            }
        }

        assertThat(sseEmitterRepository.findAllByUserId(RECEIVER_ID)).isEmpty();

        for (String title : List.of("첫 번째 누락 알림", "두 번째 누락 알림")) {
            publishAndCommit(new NotificationCreateRequestedEvent(
                    RECEIVER_ID,
                    title,
                    "연결이 끊긴 동안 생성된 알림",
                    NotificationLevel.INFO
            ));
        }

        List<NotificationCreatedEvent> events = applicationEvents
                .stream(NotificationCreatedEvent.class)
                .toList();

        assertThat(events).hasSize(3);

        try (ConfigurableApplicationContext contextB = startAdditionalNode()) {
            WebServerApplicationContext webContextB = (WebServerApplicationContext) contextB;
            int portOnB = webContextB.getWebServer().getPort();
            assertThat(portOnB).isPositive().isNotEqualTo(port);

            SseConnectionService connectionServiceOnB = contextB.getBean(SseConnectionService.class);
            HttpURLConnection connectionOnB = createSseConnection(
                    accessToken, portOnB, cursorEvent.eventId()
            );

            try {
                assertThat(connectionOnB.getResponseCode()).isEqualTo(200);

                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(connectionOnB.getInputStream(), StandardCharsets.UTF_8))) {
                    assertThat(reader.readLine()).isEqualTo(":connected");
                    assertThat(reader.readLine()).isEmpty();

                    assertNotificationFrame(reader, events.get(1));
                    assertNotificationFrame(reader, events.get(2));

                    assertThatThrownBy(() -> reader.readLine())
                            .isInstanceOf(SocketTimeoutException.class);
                }
            } finally {
                try {
                    connectionServiceOnB.closeAllLocalConnections();
                } finally {
                    connectionOnB.disconnect();
                }
            }
        }
    }

    @Test
    @DisplayName("복구 중 새 이벤트와 중복 신호가 도착해도 기존 이력과 새 알림을 순서대로 한 번씩 전달한다.")
    void deliversEventsArrivingDuringRecoveryWithoutDuplicates(
            ApplicationEvents applicationEvents
    ) throws Exception {
        for (String title : List.of("기준 알림", "복구할 알림")) {
            publishAndCommit(new NotificationCreateRequestedEvent(
                    RECEIVER_ID, title, "복구 전 생성", NotificationLevel.INFO
            ));
        }

        List<NotificationCreatedEvent> initialEvents = applicationEvents
                .stream(NotificationCreatedEvent.class)
                .toList();

        assertThat(initialEvents).hasSize(2);
        NotificationCreatedEvent cursorEvent = initialEvents.get(0);
        NotificationCreatedEvent replayEvent = initialEvents.get(1);

        CountDownLatch snapshotReady = new CountDownLatch(1);
        CountDownLatch allowRecovery = new CountDownLatch(1);
        ConcurrentHashMap<UUID, Integer> processedSignals = new ConcurrentHashMap<>();

        doAnswer(invocation -> {
            SseHistorySnapshot snapshot = (SseHistorySnapshot) invocation.callRealMethod();
            snapshotReady.countDown();
            assertThat(allowRecovery.await(15, TimeUnit.SECONDS))
                    .as("스냅샷 확보 후 복구 재개가 허용되어야 한다.")
                    .isTrue();
            return snapshot;
        }).when(historyStore).snapshot(RECEIVER_ID, cursorEvent.eventId());

        doAnswer(invocation -> {
            SseEvent<?> event = invocation.getArgument(0);
            invocation.callRealMethod();
            processedSignals.merge(event.eventId(), 1, Integer::sum);
            return null;
        }).when(sseDeliveryService).sendToLocalConnections(any());

        HttpURLConnection connection = createSseConnection(accessToken, port, cursorEvent.eventId());
        connection.setReadTimeout(15_000);
        ExecutorService executor = Executors.newSingleThreadExecutor();

        try {
            Future<Integer> response = executor.submit(connection::getResponseCode);

            assertThat(snapshotReady.await(5, TimeUnit.SECONDS))
                    .as("실제 Redis 스냅샷을 확보해야 한다.")
                    .isTrue();

            publishAndCommit(new NotificationCreateRequestedEvent(
                    RECEIVER_ID,
                    "복구 중 새 알림",
                    "스냅샷 확보 이후 생성",
                    NotificationLevel.INFO
            ));

            List<NotificationCreatedEvent> events = applicationEvents
                    .stream(NotificationCreatedEvent.class)
                    .toList();

            assertThat(events).hasSize(3);
            NotificationCreatedEvent liveEvent = events.get(2);

            sseEventPublisher.publish(SseEvent.createNotificationEvent(
                    liveEvent.eventId(), RECEIVER_ID, liveEvent.notificationDto()
            ));

            await()
                    .atMost(5, TimeUnit.SECONDS)
                    .untilAsserted(() ->
                            assertThat(processedSignals.getOrDefault(liveEvent.eventId(), 0))
                                    .isGreaterThanOrEqualTo(2)
                    );

            allowRecovery.countDown();

            assertThat(response.get(5, TimeUnit.SECONDS)).isEqualTo(200);

            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8))) {
                assertThat(reader.readLine()).isEqualTo(":connected");
                assertThat(reader.readLine()).isEmpty();

                assertNotificationFrame(reader, replayEvent);
                assertNotificationFrame(reader, liveEvent);

                assertThatThrownBy(() -> reader.readLine())
                        .isInstanceOf(SocketTimeoutException.class);
            }
        } finally {
            allowRecovery.countDown();
            executor.shutdownNow();

            try {
                sseConnectionService.closeAllLocalConnections();
            } finally {
                try {
                    connection.disconnect();
                } finally {
                    assertThat(executor.awaitTermination(5, TimeUnit.SECONDS))
                            .as("테스트 요청 스레드가 종료되어야 한다.")
                            .isTrue();
                }
            }
        }
    }

    @Test
    @DisplayName("브로커에 두 번째 이벤트가 먼저 도착해도 이력 저장 순서대로 한 번씩 전달한다.")
    void deliversInHistoryOrderWhenBrokerArrivalIsReversed() throws Exception {
        AtomicReference<NotificationCreatedEvent> firstEvent = new AtomicReference<>();
        AtomicReference<NotificationCreatedEvent> secondEvent = new AtomicReference<>();
        ConcurrentLinkedQueue<UUID> arrivalOrder = new ConcurrentLinkedQueue<>();

        CountDownLatch firstHistorySaved = new CountDownLatch(1);
        CountDownLatch allowFirstPublish = new CountDownLatch(1);

        doAnswer(invocation -> {
            SseEvent<?> event = invocation.getArgument(0);

            if (!RECEIVER_ID.equals(event.receiverId())) {
                return invocation.callRealMethod();
            }

            NotificationCreatedEvent capturedEvent = new NotificationCreatedEvent(
                    event.eventId(),
                    objectMapper.convertValue(event.data(), NotificationDto.class)
            );

            if (firstEvent.compareAndSet(null, capturedEvent)) {
                firstHistorySaved.countDown();
                assertThat(allowFirstPublish.await(15, TimeUnit.SECONDS))
                        .as("첫 번째 이벤트의 브로커 발행이 허용되어야 한다.")
                        .isTrue();
            } else {
                secondEvent.set(capturedEvent);
            }

            return invocation.callRealMethod();
        }).when(transportPublisher).publish(any());

        doAnswer(invocation -> {
            SseEvent<?> event = invocation.getArgument(0);
            invocation.callRealMethod();

            NotificationCreatedEvent first = firstEvent.get();
            NotificationCreatedEvent second = secondEvent.get();

            if ((first != null && first.eventId().equals(event.eventId()))
                    || (second != null && second.eventId().equals(event.eventId()))) {
                arrivalOrder.add(event.eventId());
            }

            return null;
        }).when(sseDeliveryService).sendToLocalConnections(any());

        HttpURLConnection connection = createSseConnection(accessToken);
        ExecutorService executor = Executors.newSingleThreadExecutor();

        try {
            assertThat(connection.getResponseCode()).isEqualTo(200);

            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8))) {
                assertThat(reader.readLine()).isEqualTo(":connected");
                assertThat(reader.readLine()).isEmpty();

                Future<?> firstPublish = executor.submit(() ->
                        publishAndCommit(new NotificationCreateRequestedEvent(
                                RECEIVER_ID,
                                "먼저 저장한 알림",
                                "브로커 발행을 대기하는 알림",
                                NotificationLevel.INFO
                        ))
                );

                assertThat(firstHistorySaved.await(5, TimeUnit.SECONDS))
                        .as("첫 번째 이벤트의 이력 저장이 완료되어야 한다.")
                        .isTrue();

                publishAndCommit(new NotificationCreateRequestedEvent(
                        RECEIVER_ID,
                        "나중에 저장한 알림",
                        "브로커에 먼저 도착하는 알림",
                        NotificationLevel.INFO
                ));

                NotificationCreatedEvent first = firstEvent.get();
                NotificationCreatedEvent second = secondEvent.get();

                assertThat(first).isNotNull();
                assertThat(second).isNotNull();

                assertThat(historyStore.snapshot(RECEIVER_ID, first.eventId()).entries())
                        .extracting(entry -> entry.event().eventId())
                        .containsExactly(second.eventId());

                await()
                        .atMost(5, TimeUnit.SECONDS)
                        .untilAsserted(() ->
                                assertThat(arrivalOrder).containsExactly(second.eventId())
                        );

                assertThat(firstPublish.isDone()).isFalse();

                assertNotificationFrame(reader, first);
                assertNotificationFrame(reader, second);

                allowFirstPublish.countDown();
                firstPublish.get(5, TimeUnit.SECONDS);

                await()
                        .atMost(5, TimeUnit.SECONDS)
                        .untilAsserted(() ->
                                assertThat(arrivalOrder)
                                        .containsExactly(second.eventId(), first.eventId())
                        );

                assertThatThrownBy(() -> reader.readLine())
                        .isInstanceOf(SocketTimeoutException.class);
            }
        } finally {
            allowFirstPublish.countDown();
            executor.shutdownNow();

            try {
                sseConnectionService.closeAllLocalConnections();
            } finally {
                try {
                    connection.disconnect();
                } finally {
                    assertThat(executor.awaitTermination(5, TimeUnit.SECONDS))
                            .as("테스트 발행 스레드가 종료되어야 한다.")
                            .isTrue();
                }
            }
        }
    }

    @Test
    @DisplayName("같은 사용자의 두 연결은 각자의 커서로 복구하고 이후 새 알림도 각각 한 번씩 받는다.")
    void recoversEachConnectionFromItsOwnCursorAndDeliversNewNotification(
            ApplicationEvents applicationEvents
    ) throws IOException {
        for (String title : List.of("알림 A", "알림 B", "알림 C")) {
            publishAndCommit(new NotificationCreateRequestedEvent(
                    RECEIVER_ID, title, "연결별 복구 테스트", NotificationLevel.INFO
            ));
        }

        List<NotificationCreatedEvent> initialEvents = applicationEvents
                .stream(NotificationCreatedEvent.class)
                .toList();

        assertThat(initialEvents).hasSize(3);
        NotificationCreatedEvent eventA = initialEvents.get(0);
        NotificationCreatedEvent eventB = initialEvents.get(1);
        NotificationCreatedEvent eventC = initialEvents.get(2);

        HttpURLConnection firstConnection =
                createSseConnection(accessToken, port, eventA.eventId());
        HttpURLConnection secondConnection =
                createSseConnection(accessToken, port, eventB.eventId());

        try {
            assertThat(firstConnection.getResponseCode()).isEqualTo(200);
            assertThat(secondConnection.getResponseCode()).isEqualTo(200);

            try (
                    BufferedReader firstReader = new BufferedReader(
                            new InputStreamReader(
                                    firstConnection.getInputStream(),
                                    StandardCharsets.UTF_8
                            )
                    );
                    BufferedReader secondReader = new BufferedReader(
                            new InputStreamReader(
                                    secondConnection.getInputStream(),
                                    StandardCharsets.UTF_8
                            )
                    )
            ) {
                assertThat(firstReader.readLine()).isEqualTo(":connected");
                assertThat(firstReader.readLine()).isEmpty();

                assertThat(secondReader.readLine()).isEqualTo(":connected");
                assertThat(secondReader.readLine()).isEmpty();

                assertNotificationFrame(firstReader, eventB);
                assertNotificationFrame(firstReader, eventC);
                assertNotificationFrame(secondReader, eventC);

                publishAndCommit(new NotificationCreateRequestedEvent(
                        RECEIVER_ID,
                        "알림 D",
                        "두 연결에 새로 전달할 알림",
                        NotificationLevel.INFO
                ));

                List<NotificationCreatedEvent> events = applicationEvents
                        .stream(NotificationCreatedEvent.class)
                        .toList();

                assertThat(events).hasSize(4);
                NotificationCreatedEvent eventD = events.get(3);

                assertNotificationFrame(firstReader, eventD);
                assertNotificationFrame(secondReader, eventD);

                assertThatThrownBy(() -> firstReader.readLine())
                        .isInstanceOf(SocketTimeoutException.class);
                assertThatThrownBy(() -> secondReader.readLine())
                        .isInstanceOf(SocketTimeoutException.class);
            }
        } finally {
            try {
                sseConnectionService.closeAllLocalConnections();
            } finally {
                try {
                    firstConnection.disconnect();
                } finally {
                    secondConnection.disconnect();
                }
            }
        }
    }

    @Test
    @DisplayName("한 연결을 종료해도 다른 연결과 Redis 이력을 유지하고 새 알림을 전달한다.")
    void closingOneConnectionPreservesOtherConnectionAndSharedHistory(
            ApplicationEvents applicationEvents
    ) throws IOException {
        for (String title : List.of("알림 A", "알림 B", "알림 C")) {
            publishAndCommit(new NotificationCreateRequestedEvent(
                    RECEIVER_ID, title, "연결별 복구 테스트", NotificationLevel.INFO
            ));
        }

        List<NotificationCreatedEvent> initialEvents = applicationEvents
                .stream(NotificationCreatedEvent.class)
                .toList();

        assertThat(initialEvents).hasSize(3);
        NotificationCreatedEvent eventA = initialEvents.get(0);
        NotificationCreatedEvent eventB = initialEvents.get(1);
        NotificationCreatedEvent eventC = initialEvents.get(2);

        HttpURLConnection firstConnection =
                createSseConnection(accessToken, port, eventA.eventId());
        HttpURLConnection secondConnection =
                createSseConnection(accessToken, port, eventB.eventId());

        try {
            assertThat(firstConnection.getResponseCode()).isEqualTo(200);
            var firstConnections = sseEmitterRepository.findAllByUserId(RECEIVER_ID);
            assertThat(firstConnections).hasSize(1);
            SseEmitter firstEmitter = firstConnections.values().iterator().next();

            assertThat(secondConnection.getResponseCode()).isEqualTo(200);
            assertThat(sseEmitterRepository.findAllByUserId(RECEIVER_ID)).hasSize(2);

            try (
                    BufferedReader firstReader = new BufferedReader(
                            new InputStreamReader(
                                    firstConnection.getInputStream(),
                                    StandardCharsets.UTF_8
                            )
                    );
                    BufferedReader secondReader = new BufferedReader(
                            new InputStreamReader(
                                    secondConnection.getInputStream(),
                                    StandardCharsets.UTF_8
                            )
                    )
            ) {
                assertThat(firstReader.readLine()).isEqualTo(":connected");
                assertThat(firstReader.readLine()).isEmpty();

                assertThat(secondReader.readLine()).isEqualTo(":connected");
                assertThat(secondReader.readLine()).isEmpty();

                assertNotificationFrame(firstReader, eventB);
                assertNotificationFrame(firstReader, eventC);
                assertNotificationFrame(secondReader, eventC);

                SseHistorySnapshot beforeClose =
                        historyStore.snapshot(RECEIVER_ID, eventA.eventId());
                assertThat(beforeClose.entries())
                        .extracting(entry -> entry.event().eventId())
                        .containsExactly(eventB.eventId(), eventC.eventId());

                firstEmitter.complete();

                await()
                        .atMost(5, TimeUnit.SECONDS)
                        .untilAsserted(() ->
                                assertThat(sseEmitterRepository.findAllByUserId(RECEIVER_ID))
                                        .hasSize(1)
                                        .doesNotContainValue(firstEmitter)
                        );

                assertThat(historyStore.snapshot(RECEIVER_ID, eventA.eventId()))
                        .isEqualTo(beforeClose);
                assertThat(firstReader.readLine()).isNull();

                publishAndCommit(new NotificationCreateRequestedEvent(
                        RECEIVER_ID,
                        "알림 D",
                        "남은 연결에 새로 전달할 알림",
                        NotificationLevel.INFO
                ));

                List<NotificationCreatedEvent> events = applicationEvents
                        .stream(NotificationCreatedEvent.class)
                        .toList();

                assertThat(events).hasSize(4);
                NotificationCreatedEvent eventD = events.get(3);

                assertNotificationFrame(secondReader, eventD);

                assertThatThrownBy(() -> secondReader.readLine())
                        .isInstanceOf(SocketTimeoutException.class);
            }
        } finally {
            try {
                sseConnectionService.closeAllLocalConnections();
            } finally {
                try {
                    firstConnection.disconnect();
                } finally {
                    secondConnection.disconnect();
                }
            }
        }
    }

    @Test
    @DisplayName("전체 이력이 만료되어도 재시도 없이 연결을 유지하고 이후 새알림을 전달한다.")
    void keepsConnectionAndDeliversNewNotificationAfterHistoryExpires(ApplicationEvents applicationEvents) throws IOException {
        publishAndCommit(new NotificationCreateRequestedEvent(
                RECEIVER_ID,
                "알림 A",
                "만료 전에 수신한 알림",
                NotificationLevel.INFO
        ));

        List<NotificationCreatedEvent> events = applicationEvents
                .stream(NotificationCreatedEvent.class)
                .toList();

        assertThat(events).hasSize(1);
        NotificationCreatedEvent eventA = events.get(0);

        HttpURLConnection connection = createSseConnection(accessToken, port, eventA.eventId());

        try {
            assertThat(connection.getResponseCode()).isEqualTo(200);

            try (BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8))){
                assertThat(reader.readLine()).isEqualTo(":connected");
                assertThat(reader.readLine()).isEmpty();

                Map<Connection, SseEmitter> connectionsBeforeExpiry = sseEmitterRepository.findAllByUserId(RECEIVER_ID);
                assertThat(connectionsBeforeExpiry).hasSize(1);

                SseHistorySnapshot snapshotBeforeExpiry = historyStore.snapshot(RECEIVER_ID, eventA.eventId());
                assertThat(snapshotBeforeExpiry.upperBound()).isNotNull();

                String streamKey = "%s:{%s}:stream".formatted(historyProperties.keyPrefix(), RECEIVER_ID);
                String mappingKey = "%s:{%s}:event-ids".formatted(historyProperties.keyPrefix(), RECEIVER_ID);

                assertThat(redisTemplate.hasKey(streamKey)).isTrue();
                assertThat(redisTemplate.hasKey(mappingKey)).isTrue();

                Duration shortTtl = Duration.ofMillis(200);
                assertThat(redisTemplate.expire(streamKey, shortTtl)).isTrue();
                assertThat(redisTemplate.expire(mappingKey, shortTtl)).isTrue();

                await().atMost(5, TimeUnit.SECONDS)
                        .pollInterval(100, TimeUnit.MILLISECONDS)
                        .untilAsserted(() ->{
                            assertThat(redisTemplate.hasKey(streamKey)).isFalse();
                            assertThat(redisTemplate.hasKey(mappingKey)).isFalse();
                        });

                AtomicReference<SseHistoryReadResult> expiredReadResult = new AtomicReference<>();

                doAnswer(invocation ->{
                    SseHistoryReadResult result = (SseHistoryReadResult) invocation.callRealMethod();
                    expiredReadResult.set(result);
                    return result;
                }).when(historyStore).readAfter(RECEIVER_ID, snapshotBeforeExpiry.upperBound());

                sseDeliveryService.sendToLocalConnections(
                        SseEvent.createNotificationEvent(
                                eventA.eventId(),
                                RECEIVER_ID,
                                eventA.notificationDto()
                        )
                );
                SseHistoryReadResult result = expiredReadResult.get();
                assertThat(result).isNotNull();
                assertThat(result.gapDetected()).isTrue();
                assertThat(result.entries()).isEmpty();

                assertThat(sseEmitterRepository.findAllByUserId(RECEIVER_ID))
                        .containsExactlyEntriesOf(connectionsBeforeExpiry);

                verify(retryTaskScheduler, never())
                        .schedule(any(Runnable.class), any(Instant.class));

                assertThat(redisTemplate.hasKey(streamKey)).isFalse();
                assertThat(redisTemplate.hasKey(mappingKey)).isFalse();

                publishAndCommit(
                        new NotificationCreateRequestedEvent(
                                RECEIVER_ID,
                                "알림 B",
                                "이력 만료 후 새로 저장한 알림",
                                NotificationLevel.INFO
                        )
                );

                List<NotificationCreatedEvent> eventsAfterExpiry = applicationEvents.stream(NotificationCreatedEvent.class)
                        .toList();

                assertThat(eventsAfterExpiry).hasSize(2);
                NotificationCreatedEvent eventB = eventsAfterExpiry.get(1);

                assertNotificationFrame(reader, eventB);
                assertThat(sseEmitterRepository.findAllByUserId(RECEIVER_ID))
                        .containsExactlyEntriesOf(connectionsBeforeExpiry);

                assertThat(redisTemplate.hasKey(streamKey)).isTrue();
                assertThat(redisTemplate.hasKey(mappingKey)).isTrue();
                assertThatThrownBy(() -> reader.readLine())
                        .isInstanceOf(SocketTimeoutException.class);

                verify(retryTaskScheduler, never())
                        .schedule(any(Runnable.class), any(Instant.class));

            }
        } finally {
            try {
                sseConnectionService.closeAllLocalConnections();
            } finally {
                connection.disconnect();
            }
        }
    }

    private void assertNotificationFrame(
            BufferedReader reader,
            NotificationCreatedEvent expectedEvent
    ) throws IOException {
        assertThat(reader.readLine())
                .isEqualTo("id:" + expectedEvent.eventId());
        assertThat(reader.readLine())
                .isEqualTo("event:notifications");

        String dataLine = reader.readLine();
        assertThat(dataLine).startsWith("data:");
        assertThat(objectMapper.readTree(dataLine.substring("data:".length())))
                .isEqualTo(objectMapper.valueToTree(expectedEvent.notificationDto()));

        assertThat(reader.readLine()).isEmpty();
    }

    private HttpURLConnection createSseConnection(String token, int serverPort) throws IOException {
        return createSseConnection(token, serverPort, null);
    }

    private HttpURLConnection createSseConnection(String token) throws IOException {
        return createSseConnection(token, port, null);
    }

    /**
     * Bearer 인증으로 실제 HTTP 스트림을 연다. 연결·읽기 제한 시간으로 실패 시 무한 대기를 막는다.
     * 호출자는 필요한 프레임만 읽고 서버의 연결 정리와 클라이언트 disconnect를 모두 수행한다.
     */
    private HttpURLConnection createSseConnection(String token, int serverPort, UUID lastEventId) throws IOException {
        String url = "http://localhost:" + serverPort + "/api/sse";
        if (lastEventId != null) {
            url += "?lastEventId=" + lastEventId;
        }

        HttpURLConnection connection = (HttpURLConnection) URI.create(url)
                .toURL()
                .openConnection();

        connection.setRequestMethod("GET");
        connection.setRequestProperty(
                HttpHeaders.AUTHORIZATION,
                "Bearer "+ token
        );
        connection.setRequestProperty(
                HttpHeaders.ACCEPT,
                MediaType.TEXT_EVENT_STREAM_VALUE
        );
        connection.setConnectTimeout(5_000);
        connection.setReadTimeout(5_000);
        connection.setInstanceFollowRedirects(false);

        return connection;
    }

    /** 업무 트랜잭션 안에서 요청을 발행하고 커밋하여 알림 저장·전송 경로를 시작한다. */
    private void publishAndCommit(NotificationCreateRequestedEvent event){
        transactionTemplate.executeWithoutResult(status ->
                eventPublisher.publishEvent(event));
    }

    private void insertUser(UUID userId, String nickname) {
        jdbcTemplate.update("""
            INSERT INTO users (id, email, nickname, role, enabled, created_at)
            VALUES (?, ?, ?, 'USER', true, CURRENT_TIMESTAMP)
        """,userId,
                userId + "@test.example",
                nickname);
    }

    /**
     * A와 같은 DB·Redis·채널을 사용하면서 빈·emitter 저장소·HTTP 포트는 독립적인 B를 실행한다.
     * 테스트가 직접 만든 컨텍스트이므로 A의 DynamicPropertySource를 상속한다고 가정하지 않는다.
     * 필요한 설정을 직접 전달하고, 호출부의 try-with-resources에서 종료한다.
     */
    private ConfigurableApplicationContext startAdditionalNode(){
        return new SpringApplicationBuilder(PlaylistForEveryoneApplication.class)
                .web(WebApplicationType.SERVLET)
                .registerShutdownHook(false)
                .run(
                        "--spring.profiles.active=test",
                        "--server.port=0",
                        "--spring.datasource.url=" + postgres.getJdbcUrl(),
                        "--spring.datasource.username="+postgres.getUsername(),
                        "--spring.datasource.password="+postgres.getPassword(),
                        "--spring.datasource.driver-class-name=org.postgresql.Driver",
                        "--spring.jpa.hibernate.ddl-auto=validate",
                        "--spring.flyway.enabled=true",
                        "--spring.docker.compose.enabled=false",
                        "--spring.data.redis.host="+redis.getHost(),
                        "--spring.data.redis.port=" + redis.getMappedPort(6379),
                        "--everyone.sse.transport=redis",
                        "--everyone.sse.redis.channel=test:sse:notification",
                        "--everyone.sse.heartbeat-interval=1h",
                        "--everyone.sse.connection-timeout=30s",
                        "--everyone.sse.subscription-enabled=true"
                );
    }
}
