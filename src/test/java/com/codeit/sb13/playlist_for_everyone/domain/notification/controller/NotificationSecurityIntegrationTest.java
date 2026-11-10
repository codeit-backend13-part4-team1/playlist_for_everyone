package com.codeit.sb13.playlist_for_everyone.domain.notification.controller;

import com.codeit.sb13.playlist_for_everyone.domain.auth.jwt.JwtProperties;
import com.codeit.sb13.playlist_for_everyone.domain.auth.jwt.JwtTokenProvider;
import com.codeit.sb13.playlist_for_everyone.domain.auth.repository.UserSessionRepository;
import com.codeit.sb13.testsupport.auth.InMemoryUserSessionConfig;
import com.codeit.sb13.testsupport.auth.TestTokens;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import io.jsonwebtoken.security.SignatureException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.MockMvcBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.web.csrf.CsrfFilter;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import javax.crypto.SecretKey;
import java.time.*;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * MockMvc 요청에 실제 JWT 필터·사용자 조회·알림 서비스·PostgreSQL을 연결하는 통합 테스트다.
 *
 * <p>로그인 API 대신 JwtTokenProvider로 토큰을 만들고, DB에 두 사용자를 준비하여
 * 본인 알림만 조회·삭제되는지 확인한다. 인증 실패 사례는 401 응답과 삭제 대상의 보존을 확인한다.
 * 실제 HTTP 서버·Redis 전송은 사용하지 않으며, CSRF 검증은 내부 임시 설정으로 제외한다.
 *
 * <p>테스트 전체에 롤백 트랜잭션을 두지 않는다. 서비스에서 커밋한 결과를 JDBC로 확인하고
 * 각 사례가 만든 데이터는 직접 정리한다. 기본 test 프로필과 컨테이너의 접속 정보를 사용한다.
 */
@SpringBootTest(
        properties = {
                "spring.flyway.enabled=true",
                "spring.jpa.hibernate.ddl-auto=validate",
                "spring.docker.compose.enabled=false"
        }
)
@AutoConfigureMockMvc
@Import({NotificationSecurityIntegrationTest.CsrfDisabledTestConfig.class, InMemoryUserSessionConfig.class})
@Testcontainers
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@DisplayName("알림 JWT 인증 통합 테스트")
public class NotificationSecurityIntegrationTest {

    @Container
    static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16");

    @DynamicPropertySource
    static void configurePostgres(DynamicPropertyRegistry registry){
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.datasource.driver-class-name", postgres::getDriverClassName);
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @Autowired
    private UserSessionRepository userSessionRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private JwtProperties jwtProperties;

    private final UUID receiverId = UUID.randomUUID();

    private final UUID otherReceiverId = UUID.randomUUID();

    @BeforeEach
    void setUp(){
        // JWT 필터의 실제 사용자 조회와 알림 수신자 외래 키를 모두 만족하도록 먼저 저장한다.
        insertUser(receiverId, "알림을 조회하는 사용자");
        insertUser(otherReceiverId, "다른 사용자");
    }

    @AfterEach
    void cleanUp(){
        // 외래 키 순서에 맞춰 알림을 먼저 제거하며, 이 사례의 두 사용자 데이터만 정리한다.
        jdbcTemplate.update("""
            DELETE FROM notifications
            WHERE receiver_id IN (?, ?)
        """, receiverId, otherReceiverId);

        jdbcTemplate.update("""
            DELETE FROM users
            WHERE id IN (?, ?)
        """, receiverId, otherReceiverId);
    }

    /**
     * 이 클래스의 MockMvc 요청에서만 CSRF 검사를 제외한다.
     * JWT 인증과 사용자별 데이터 접근은 계속 검증한다.
     *
     * TODO(#124): 실제 CSRF 토큰을 준비하도록 테스트를 변경하고,
     * 이 임시 설정과 클래스의 @Import를 제거한다.
     */
    @TestConfiguration(proxyBeanMethods = false)
    static class CsrfDisabledTestConfig{
        @Bean
        MockMvcBuilderCustomizer disableCsrfForNotificationSecurityTests(){
            return builder -> builder
                    .defaultRequest(get("/").with(request ->{
                        CsrfFilter.skipRequest(request);
                        return request;
                    }));
        }
    }

    @Test
    @DisplayName("JWT 없이 알림 목록을 조회하면 401과 공통 인증 오류 응답을 반환한다.")
    void getNotifications_returnsUnauthorizedWithoutToken() throws Exception {
        mockMvc.perform(get("/api/notifications")
                .param("limit", "20")
                .param("sortDirection","DESCENDING")
                .param("sortBy", "createdAt"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.exceptionName").value("UnauthorizedException"))
                .andExpect(jsonPath("$.message").value("인증이 필요합니다."))
                .andExpect(jsonPath("$.details").isMap())
                .andExpect(jsonPath("$.details").isEmpty());
    }

    @Test
    @DisplayName("유효한 JWT로 알림 목록을 조회하면 본인 알림과 본인의 전체 알림 수만 반환한다.")
    void getNotifications_returnsOnlyOwnNotificationsWithValidToken() throws Exception {
        UUID olderNotificationId = UUID.randomUUID();
        UUID newerNotificationId = UUID.randomUUID();
        UUID otherNotificationId = UUID.randomUUID();

        LocalDateTime baseTime = getNotificationTimestamp();

        insertNotification(olderNotificationId, receiverId, baseTime);
        insertNotification(newerNotificationId, receiverId, baseTime.plusMinutes(2));
        insertNotification(otherNotificationId, otherReceiverId, baseTime.plusMinutes(1));

        String accessToken = TestTokens.accessToken(jwtTokenProvider, userSessionRepository, receiverId);

        mockMvc.perform(get("/api/notifications")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                .param("limit", "20")
                .param("sortDirection", "DESCENDING")
                .param("sortBy", "createdAt"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[0].id").value(newerNotificationId.toString()))
                .andExpect(jsonPath("$.data[0].receiverId").value(receiverId.toString()))
                .andExpect(jsonPath("$.data[1].id").value(olderNotificationId.toString()))
                .andExpect(jsonPath("$.data[1].receiverId").value(receiverId.toString()))
                .andExpect(jsonPath("$.totalCount").value(2))
                .andExpect(jsonPath("$.hasNext").value(false));
    }

    @Test
    @DisplayName("본인 알림 삭제는 204를 반환하고 목록·전체 개수에 반영되며 다른 사용자의 알림은 유지된다.")
    void deleteNotifications_deletesOwnNotificationWithValidToken() throws Exception {
        UUID notificationId = UUID.randomUUID();
        UUID remainingNotificationId = UUID.randomUUID();
        UUID otherNotificationId = UUID.randomUUID();

        LocalDateTime createdAt = getNotificationTimestamp();

        insertNotification(notificationId, receiverId, createdAt);
        insertNotification(remainingNotificationId, receiverId, createdAt.minusMinutes(1));
        insertNotification(otherNotificationId, otherReceiverId, createdAt);

        String accessToken = TestTokens.accessToken(jwtTokenProvider, userSessionRepository, receiverId);

        mockMvc.perform(delete("/api/notifications/{notificationId}", notificationId)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));

        mockMvc.perform(get("/api/notifications")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                .param("limit", "20")
                .param("sortDirection","DESCENDING")
                .param("sortBy","createdAt"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].id").value(remainingNotificationId.toString()))
                .andExpect(jsonPath("$.data[0].receiverId").value(receiverId.toString()))
                .andExpect(jsonPath("$.totalCount").value(1))
                .andExpect(jsonPath("$.hasNext").value(false));

        assertThat(countNotificationsById(notificationId)).isZero();
        assertThat(countNotificationsById(remainingNotificationId)).isEqualTo(1L);
        assertThat(countNotificationsById(otherNotificationId)).isEqualTo(1L);
    }

    @Test
    @DisplayName("JWT 없이 알림 삭제를 요청하면 401과 공통 인증 오류 응답을 반환하고 알림을 유지한다.")
    void deleteNotifications_returnsUnauthorizedWithoutToken() throws Exception {
        UUID notificationId = UUID.randomUUID();
        insertNotification(notificationId, receiverId, getNotificationTimestamp());

        mockMvc.perform(delete("/api/notifications/{notificationId}", notificationId))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.exceptionName").value("UnauthorizedException"))
                .andExpect(jsonPath("$.message").value("인증이 필요합니다."))
                .andExpect(jsonPath("$.details").isMap())
                .andExpect(jsonPath("$.details").isEmpty());

        assertThat(countNotificationsById(notificationId)).isEqualTo(1L);
    }

    @Test
    @DisplayName("다른 사용자의 알림 삭제를 요청하면 204를 반환하고 두 사용자의 알림을 모두 유지한다.")
    void deleteNotifications_preservesOtherUsersNotification() throws Exception {
        UUID ownNotificationId = UUID.randomUUID();
        UUID otherNotificationId = UUID.randomUUID();
        LocalDateTime createdAt = getNotificationTimestamp();

        insertNotification(ownNotificationId, receiverId, createdAt);
        insertNotification(otherNotificationId, otherReceiverId, createdAt);

        String accessToken = TestTokens.accessToken(jwtTokenProvider, userSessionRepository, receiverId);

        mockMvc.perform(delete("/api/notifications/{notificationId}", otherNotificationId)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));

        assertThat(countNotificationsById(ownNotificationId)).isEqualTo(1L);
        assertThat(countNotificationsById(otherNotificationId)).isEqualTo(1L);
    }

    @Test
    @DisplayName("같은 본인 알림을 두 번 삭제하면 두 요청 모두 204를 반환하고 삭제 상태를 유지한다.")
    void deleteNotifications_returnsNoContentForRepeatedRequest() throws Exception {
        UUID notificationId = UUID.randomUUID();
        insertNotification(notificationId, receiverId, getNotificationTimestamp());

        String accessToken = TestTokens.accessToken(jwtTokenProvider, userSessionRepository, receiverId);

        mockMvc.perform(delete("/api/notifications/{notificationId}", notificationId)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));

        assertThat(countNotificationsById(notificationId)).isZero();

        mockMvc.perform(delete("/api/notifications/{notificationId}", notificationId)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));

        assertThat(countNotificationsById(notificationId)).isZero();
    }

    @Test
    @DisplayName("존재하지 않는 알림을 삭제하면 204를 반환하고 기존 알림을 유지한다.")
    void deleteNotifications_returnsNoContentForMissingNotification() throws Exception {
        UUID existingNotificationId = UUID.randomUUID();
        UUID missingNotificationId = UUID.randomUUID();

        insertNotification(existingNotificationId, receiverId, getNotificationTimestamp());

        String accessToken = TestTokens.accessToken(jwtTokenProvider, userSessionRepository, receiverId);

        assertThat(countNotificationsById(missingNotificationId)).isZero();

        mockMvc.perform(delete("/api/notifications/{notificationId}", missingNotificationId)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));

        assertThat(countNotificationsById(missingNotificationId)).isZero();
        assertThat(countNotificationsById(existingNotificationId)).isEqualTo(1L);

    }

    @Test
    @DisplayName("만료된 JWT로 알림 목록을 조회하면 401과 공통 인증 오류 응답을 반환한다.")
    void getNotifications_returnsUnauthorizedWithExpiredToken() throws Exception {
        String expiredToken = createExpiredAccessToken(receiverId);

        assertThatThrownBy(()-> jwtTokenProvider.parseAccess(expiredToken))
                .isInstanceOf(ExpiredJwtException.class);

        mockMvc.perform(get("/api/notifications")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + expiredToken)
                .param("limit", "20")
                .param("sortDirection", "DESCENDING")
                .param("sortBy", "createdAt"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.exceptionName").value("UnauthorizedException"))
                .andExpect(jsonPath("$.message").value("인증이 필요합니다."))
                .andExpect(jsonPath("$.details").isMap())
                .andExpect(jsonPath("$.details").isEmpty());
    }

    @Test
    @DisplayName("만료된 JWT로 알림 삭제를 요청하면 401과 공통 인증 오류 응답을 반환하고 알림을 유지한다.")
    void deleteNotifications_returnsUnauthorizedWithExpiredToken() throws Exception {
        UUID notificationId = UUID.randomUUID();
        insertNotification(notificationId, receiverId, getNotificationTimestamp());
        String expiredToken = createExpiredAccessToken(receiverId);

        assertThatThrownBy(()-> jwtTokenProvider.parseAccess(expiredToken))
                .isInstanceOf(ExpiredJwtException.class);

        mockMvc.perform(delete("/api/notifications/{notificationId}", notificationId)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + expiredToken))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.exceptionName").value("UnauthorizedException"))
                .andExpect(jsonPath("$.message").value("인증이 필요합니다."))
                .andExpect(jsonPath("$.details").isMap())
                .andExpect(jsonPath("$.details").isEmpty());

        assertThat(countNotificationsById(notificationId)).isEqualTo(1L);
    }

    @Test
    @DisplayName("변조된 JWT로 알림 목록을 조회하면 401과 공통 인증 오류 응답을 반환한다.")
    void getNotifications_returnsUnauthorizedWithTamperedToken() throws Exception {
        String tamperedToken = createTamperedAccessToken(receiverId);

        assertThatThrownBy(()-> jwtTokenProvider.parseAccess(tamperedToken))
                .isInstanceOf(SignatureException.class);

        mockMvc.perform(get("/api/notifications")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + tamperedToken)
                .param("limit", "20")
                .param("sortDirection", "DESCENDING")
                .param("sortBy", "createdAt"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.exceptionName").value("UnauthorizedException"))
                .andExpect(jsonPath("$.message").value("인증이 필요합니다."))
                .andExpect(jsonPath("$.details").isMap())
                .andExpect(jsonPath("$.details").isEmpty());

    }

    @Test
    @DisplayName("변조된 JWT로 알림 삭제를 요청하면 401과 공통 인증 오류 응답을 반환하고 알림을 유지한다.")
    void deleteNotifications_returnsUnauthorizedWithTamperedToken() throws Exception {
        UUID notificationId = UUID.randomUUID();
        insertNotification(notificationId, receiverId, getNotificationTimestamp());
        String tamperedToken = createTamperedAccessToken(receiverId);

        assertThatThrownBy(()-> jwtTokenProvider.parseAccess(tamperedToken))
                .isInstanceOf(SignatureException.class);

        mockMvc.perform(delete("/api/notifications/{notificationId}", notificationId)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + tamperedToken))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.exceptionName").value("UnauthorizedException"))
                .andExpect(jsonPath("$.message").value("인증이 필요합니다."))
                .andExpect(jsonPath("$.details").isMap())
                .andExpect(jsonPath("$.details").isEmpty());

        assertThat(countNotificationsById(notificationId)).isEqualTo(1L);
    }

    private void insertUser(UUID userId, String nickname){
        jdbcTemplate.update("""
            INSERT INTO users (id, email, nickname, role, enabled, created_at)
            VALUES (?, ?, ?, 'USER', true, CURRENT_TIMESTAMP)
        """,userId, userId+"@test.example", nickname);
    }

    /** 조회·삭제 검증용 행을 직접 준비한다. 알림 생성 이벤트와 SSE 전송은 이 테스트의 대상이 아니다. */
    private void insertNotification(UUID notificationId, UUID receiverId, LocalDateTime createdAt){
        jdbcTemplate.update("""
            INSERT INTO notifications (id, receiver_id, title, content, level, created_at)
            VALUES (?, ?, '알림 제목', '알림 내용', 'INFO', ?)
        """, notificationId, receiverId, createdAt);
    }

    private Long countNotificationsById(UUID notificationId) {
        return jdbcTemplate.queryForObject("""
                    SELECT COUNT(*)
                    FROM notifications
                    WHERE id =?
                """, Long.class, notificationId);
    }

    private LocalDateTime getNotificationTimestamp() {
        return LocalDateTime.of(2026, 11, 4, 12, 0);
    }

    /**
     * 실제 설정의 키·유효기간을 유지하고 발급 시각만 과거로 옮긴다.
     * 대기 없이 만료 토큰을 만들며, 각 사례에서 ExpiredJwtException인지 먼저 확인한다.
     */
    private String createExpiredAccessToken(UUID userId){
        SecretKey signingKey = Keys.hmacShaKeyFor(Decoders.BASE64.decode(jwtProperties.secretKey()));

        Duration validity = jwtProperties.accessTokenValidity();
        Instant issuedAt = Instant.now()
                .minus(validity)
                .minus(Duration.ofMinutes(1));

        JwtTokenProvider pastTokenProvider = new JwtTokenProvider(
                signingKey,
                validity,
                jwtProperties.refreshTokenValidity(),
                Clock.fixed(issuedAt, ZoneOffset.UTC)
        );

        return pastTokenProvider.createAccessToken(userId, UUID.randomUUID());
    }

    /** 헤더·페이로드를 유지하고 서명만 변경한다. 각 사례에서 SignatureException인지 먼저 확인한다. */
    private String createTamperedAccessToken(UUID userId){
        String validToken = TestTokens.accessToken(jwtTokenProvider, userSessionRepository, userId);
        String[] tokenParts = validToken.split("\\.");

        String signature = tokenParts[2];
        String replacement = signature.startsWith("A") ? "B" : "A";
        tokenParts[2] = replacement + signature.substring(1);

        return String.join(".", tokenParts);
    }
}
