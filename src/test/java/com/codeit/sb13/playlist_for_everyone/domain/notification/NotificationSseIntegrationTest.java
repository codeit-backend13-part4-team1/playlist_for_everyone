package com.codeit.sb13.playlist_for_everyone.domain.notification;

import com.codeit.sb13.playlist_for_everyone.PlaylistForEveryoneApplication;
import com.codeit.sb13.playlist_for_everyone.domain.auth.jwt.JwtTokenProvider;
import com.codeit.sb13.playlist_for_everyone.domain.auth.repository.UserSessionRepository;
import com.codeit.sb13.playlist_for_everyone.domain.notification.dto.event.NotificationCreateRequestedEvent;
import com.codeit.sb13.playlist_for_everyone.domain.notification.entity.Notification;
import com.codeit.sb13.playlist_for_everyone.domain.notification.entity.NotificationLevel;
import com.codeit.sb13.playlist_for_everyone.domain.notification.event.NotificationCreatedEvent;
import com.codeit.sb13.playlist_for_everyone.domain.notification.repository.NotificationRepository;
import com.codeit.sb13.playlist_for_everyone.global.sse.repository.SseEmitterRepository;
import com.codeit.sb13.playlist_for_everyone.global.sse.service.SseConnectionService;
import com.codeit.sb13.testsupport.auth.TestTokens;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.web.server.context.WebServerApplicationContext;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
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
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 실제 PostgreSQL·Redis·HTTP 서버로 공통 알림 생성 요청부터 SSE 프레임 수신까지 검증한다.
 *
 * <p>DB에 사용자를 준비하고 실제 JwtTokenProvider로 발급한 토큰을 사용한다. JWT 필터와
 * 사용자 조회는 실제로 실행하지만, 회원가입·로그인 API를 통한 토큰 발급 과정은 검증하지 않는다.
 * 서버 간 사례는 동일 JVM의 독립 Spring 컨텍스트 두 개를 사용하며 별도 OS 프로세스는 아니다.
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
    private UserSessionRepository userSessionRepository;

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

        accessToken = TestTokens.accessToken(jwtTokenProvider, userSessionRepository, RECEIVER_ID);
        otherAccessToken = TestTokens.accessToken(jwtTokenProvider, userSessionRepository, OTHER_USER_ID);
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

    private HttpURLConnection createSseConnection(String token) throws IOException {
        return createSseConnection(token, port);
    }

    /**
     * Bearer 인증으로 실제 HTTP 스트림을 연다. 연결·읽기 제한 시간으로 실패 시 무한 대기를 막는다.
     * 호출자는 필요한 프레임만 읽고 서버의 연결 정리와 클라이언트 disconnect를 모두 수행한다.
     */
    private HttpURLConnection createSseConnection(String token, int serverPort) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) URI.create("http://localhost:" + serverPort + "/api/sse")
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
