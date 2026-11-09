package com.codeit.sb13.playlist_for_everyone.domain.follow.controller;

import com.codeit.sb13.playlist_for_everyone.domain.auth.jwt.JwtProperties;
import com.codeit.sb13.playlist_for_everyone.domain.auth.jwt.JwtTokenProvider;
import com.codeit.sb13.playlist_for_everyone.domain.follow.event.UserFollowedEvent;
import com.codeit.sb13.playlist_for_everyone.domain.notification.event.listener.NotificationDomainEventListener;
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
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import javax.crypto.SecretKey;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * 실제 JWT를 HTTP 요청 헤더에 넣어 보안 필터부터 서비스·PostgreSQL까지 연결해 검증한다.
 * 필터를 끄고 서비스를 Mock으로 대체하는 FollowControllerTest와 달리 인증·소유자 인가도 실행한다.
 *
 * <p>테스트 전체를 트랜잭션으로 감싸지 않는다. 준비한 사용자는 FollowSaver의 REQUIRES_NEW
 * 트랜잭션과 동시 요청 스레드에서도 조회할 수 있어야 하며, 요청이 커밋한 데이터는 직접 정리한다.
 *
 * <p>ApplicationEvents는 이벤트 발행 기록이다. 커밋 후 수신 여부는
 * FollowServiceIntegrationTest의 테스트 전용 AFTER_COMMIT 리스너에서 확인한다.
 * CSRF와 실제 알림 저장·SSE 전달은 이 테스트의 검증 범위에 포함하지 않는다.
 */
@RecordApplicationEvents
@SpringBootTest(
        properties = {
                "spring.flyway.enabled=true",
                "spring.jpa.hibernate.ddl-auto=validate",
                "spring.docker.compose.enabled=false"
        }
)
@Import(FollowSecurityIntegrationTest.CsrfDisabledTestConfig.class)
@AutoConfigureMockMvc
@Testcontainers
// 종료된 컨테이너의 접속 정보를 가진 애플리케이션 컨텍스트가 다른 테스트에 재사용되지 않게 한다.
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@DisplayName("팔로우 JWT 인증·인가 통합 테스트")
public class FollowSecurityIntegrationTest {

    @Container
    static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16");

    // 기본 test 프로필의 H2 접속 정보를 덮어쓴다. 스키마는 Flyway로 적용하고 JPA는 매핑을 검증한다.
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
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private JwtProperties jwtProperties;

    @MockitoBean
    private NotificationDomainEventListener notificationDomainEventListener;

    private final UUID followerId = UUID.randomUUID();

    private final UUID followeeId = UUID.randomUUID();

    private final UUID otherFollowerId = UUID.randomUUID();

    @BeforeEach
    void setUp(){
        // JWT 필터가 조회할 실제 사용자를 준비한다. 회원가입 API 동작은 이 테스트의 관심사가 아니다.
        insertUser(followerId, "팔로우하는 사용자");
        insertUser(followeeId, "팔로우받는 사용자");
        insertUser(otherFollowerId, "다른 팔로우하는 사용자");
    }

    @AfterEach
    void cleanUp(){
        // 요청에서 이미 커밋한 행을 지운다. FK 순서에 맞춰 이번 테스트의 관계를 먼저, 사용자를 나중에 삭제한다.
        jdbcTemplate.update("""
            DELETE FROM follows
            WHERE follower_id IN (?, ?, ?)
                OR followee_id IN (?, ?, ?)
        """, followeeId, followerId, otherFollowerId,
                followeeId, followerId, otherFollowerId);
        jdbcTemplate.update("DELETE FROM users WHERE id IN (?, ?, ?)", followeeId, followerId, otherFollowerId);
    }

    @Test
    @DisplayName("유효한 JWT로 신규 팔로우를 요청하면 관계를 저장하고 201을 반환하며 생성이벤트를 한 번 발행한다.")
    void follow_returnsCreatedWithAuthenticatedUser(ApplicationEvents applicationEvents) throws Exception {
        String accessToken = jwtTokenProvider.createAccessToken(followerId);

        ResultActions result = mockMvc.perform(post("/api/follows")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "followeeId": "%s"
                                }
                                """.formatted(followeeId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNotEmpty())
                .andExpect(jsonPath("$.followerId").value(followerId.toString()))
                .andExpect(jsonPath("$.followeeId").value(followeeId.toString()));

        UUID savedFollowId = jdbcTemplate.queryForObject("""
                        SELECT id
                        FROM follows
                        WHERE follower_id = ?
                            AND followee_id = ?
                        """,
                UUID.class,
                followerId,
                followeeId);

        result.andExpect(jsonPath("$.id").value(savedFollowId.toString()));
        // 발행 당시 DB에서 조회한 이름과 두 사용자 ID를 확인한다. 알림 소비 성공을 확인하는 단언은 아니다.
        assertThat(applicationEvents.stream(UserFollowedEvent.class))
                .containsExactly(new UserFollowedEvent(followerId, "팔로우하는 사용자", followeeId));
    }

    @Test
    @DisplayName("유효한 JWT로 중복 팔로우를 요청하면 200을 반환하고 관계 ID·생성 시각·행 수를 유지한다.")
    void follow_returnsExistingRelationForDuplicateRequest() throws Exception {
        String accessToken = jwtTokenProvider.createAccessToken(followerId);

        String requestBody = """
                {
                    "followeeId": "%s"
                }
                """.formatted(followeeId);

        String firstResponse = mockMvc.perform(post("/api/follows")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();

        String findRelationSql = """
                    SELECT id, created_at
                    FROM follows
                    WHERE follower_id = ?
                        AND followee_id = ?
                """;

        List<Map<String, Object>> beforeRows = jdbcTemplate.queryForList(findRelationSql, followerId, followeeId);
        assertThat(beforeRows).hasSize(1);

        mockMvc.perform(post("/api/follows")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody))
                .andExpect(status().isOk())
                .andExpect(content().json(firstResponse));

        List<Map<String, Object>> afterRows = jdbcTemplate.queryForList(findRelationSql, followerId, followeeId);

        assertThat(afterRows)
                .hasSize(1)
                .containsExactlyElementsOf(beforeRows);
    }

    @Test
    @DisplayName("유효한 JWT로 본인의 팔로우를 취소하면 해당 관계만 삭제하고 다른 관계와 두 사용자는 유지한다.")
    void unfollow_returnsNoContentForOwnRelation() throws Exception {
        UUID followId = UUID.randomUUID();
        followInsert(followId);

        UUID reverseFollowId = UUID.randomUUID();
        jdbcTemplate.update("""
            INSERT INTO follows (id, follower_id, followee_id, created_at)
            VALUES (?, ?, ?, CURRENT_TIMESTAMP)
            """, reverseFollowId, followeeId, followerId);

        String accessToken = jwtTokenProvider.createAccessToken(followerId);
        mockMvc.perform(delete("/api/follows/{followId}", followId)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));

        Long remainingFollowCount = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM follows WHERE id = ?",
                Long.class, followId);

        assertThat(remainingFollowCount).isZero();

        List<UUID> remainingUserIds = jdbcTemplate.queryForList("SELECT id FROM users WHERE id IN (?, ?)",
                UUID.class, followerId, followeeId);

        List<UUID> remainingReverseFollowIds = jdbcTemplate.queryForList("""
                    SELECT id
                    FROM follows
                    WHERE follower_id = ?
                        AND followee_id = ?
                """,
                UUID.class, followeeId, followerId);

        assertThat(remainingUserIds)
                .containsExactlyInAnyOrder(followerId, followeeId);

        assertThat(remainingReverseFollowIds)
                .containsExactly(reverseFollowId);
    }

    @Test
    @DisplayName("유효한 JWT로 같은 팔로우를 두 번 취소하면 두 요청 모두 204를 반환하고 삭제 상태를 유지한다.")
    void unfollow_returnsNoContentForRepeatedRequest() throws Exception {
        UUID followId = UUID.randomUUID();
        followInsert(followId);

        String accessToken = jwtTokenProvider.createAccessToken(followerId);
        String countRelationSql = "SELECT COUNT(*) FROM follows WHERE id =?";

        mockMvc.perform(delete("/api/follows/{followId}", followId)
                .header(HttpHeaders.AUTHORIZATION, "Bearer "+accessToken))
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));

        Long countAfterFirstRequest = jdbcTemplate.queryForObject(countRelationSql, Long.class, followId);
        assertThat(countAfterFirstRequest).isZero();

        mockMvc.perform(delete("/api/follows/{followId}",followId)
                .header(HttpHeaders.AUTHORIZATION, "Bearer "+accessToken))
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));

        Long countAfterSecondRequest = jdbcTemplate.queryForObject(countRelationSql, Long.class, followId);
        assertThat(countAfterSecondRequest).isZero();
    }

    @Test
    @DisplayName("유효한 JWT로 다른 사용자의 팔로우를 취소하면 공통 오류 응답으로 403을 반환하고 관계 정보를 유지한다.")
    void unfollow_returnsForbiddenForAnotherUsersRelation() throws Exception {
        UUID followId = UUID.randomUUID();
        followInsert(followId);

        String findRelationSql = """
                SELECT id, follower_id, followee_id, created_at
                FROM follows
                WHERE id = ?
                """;

        List<Map<String, Object>> beforeRows = jdbcTemplate.queryForList(findRelationSql, followId);
        assertThat(beforeRows).hasSize(1);

        String accessToken = jwtTokenProvider.createAccessToken(followeeId);

        mockMvc.perform(delete("/api/follows/{followId}",followId)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.exceptionName").isString())
                .andExpect(jsonPath("$.exceptionName").isNotEmpty())
                .andExpect(jsonPath("$.message").value("접근 권한이 없습니다."))
                .andExpect(jsonPath("$.details").isMap())
                .andExpect(jsonPath("$.details").isEmpty())
        ;

        List<Map<String, Object>> afterRows = jdbcTemplate.queryForList(findRelationSql, followId);
        assertThat(afterRows)
                .hasSize(1)
                .containsExactlyElementsOf(beforeRows);
    }

    @Test
    @DisplayName("유효한 JWT로 존재하지 않는 팔로우를 취소하면 204를 반환하고 기존 관계를 유지한다.")
    void unfollow_returnsNoContentForMissingRelation() throws Exception {
        UUID existingFollowId = UUID.randomUUID();
        followInsert(existingFollowId);

        String findRelationSql = """
                SELECT id, follower_id, followee_id, created_at
                FROM follows
                WHERE id =?
                """;

        List<Map<String, Object>> beforeRows = jdbcTemplate.queryForList(findRelationSql, existingFollowId);
        assertThat(beforeRows).hasSize(1);

        UUID missingFollowId = UUID.randomUUID();
        String accessToken = jwtTokenProvider.createAccessToken(followerId);

        mockMvc.perform(delete("/api/follows/{followId}", missingFollowId)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));

        List<Map<String, Object>> afterRows = jdbcTemplate.queryForList(findRelationSql, existingFollowId);
        assertThat(afterRows)
                .hasSize(1)
                .containsExactlyElementsOf(beforeRows);
    }

    @Test
    @DisplayName("JWT 없이 팔로우를 등록하면 401을 반환하고 관계 저장과 이벤트 발행이 발생하지 않는다.")
    void follow_returnsUnauthorizedWithoutJwt(ApplicationEvents applicationEvents) throws Exception {
        mockMvc.perform(post("/api/follows")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {
                            "followeeId": "%s"
                        }
                        """.formatted(followeeId)))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.exceptionName").value("UnauthorizedException"))
                .andExpect(jsonPath("$.message").value("인증이 필요합니다."))
                .andExpect(jsonPath("$.details").isMap())
                .andExpect(jsonPath("$.details").isEmpty());

        Long savedFollowCount = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM follows
                WHERE followee_id = ?
                """, Long.class, followeeId);

        assertThat(savedFollowCount).isZero();
        assertThat(applicationEvents.stream(UserFollowedEvent.class))
                .isEmpty();
    }

    @Test
    @DisplayName("JWT 없이 팔로우를 취소하면 공통 오류 응답으로 401을 반환하고 기존 관계를 유지한다.")
    void unfollow_returnsUnauthorizedWithoutJwt() throws Exception {
        UUID followId = UUID.randomUUID();
        followInsert(followId);

        String findRelationSql = """
                SELECT id, follower_id, followee_id, created_at
                FROM follows
                WHERE id = ?
                """;

        List<Map<String, Object>> beforeRows = jdbcTemplate.queryForList(findRelationSql, followId);
        assertThat(beforeRows).hasSize(1);

        mockMvc.perform(delete("/api/follows/{followId}", followId))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.exceptionName").value("UnauthorizedException"))
                .andExpect(jsonPath("$.message").value("인증이 필요합니다."))
                .andExpect(jsonPath("$.details").isEmpty())
                .andExpect(jsonPath("$.details").isMap());

        List<Map<String, Object>> afterRows = jdbcTemplate.queryForList(findRelationSql, followId);
        assertThat(afterRows)
                .hasSize(1)
                .containsExactlyElementsOf(beforeRows);

    }

    @Test
    @DisplayName("JWT 없이 내 팔로우 관계를 조회하면 공통 오류 응답으로 401을 반환합니다.")
    void getFollowedByMe_returnsUnauthorizedWithoutJwt() throws Exception {
        UUID followId = UUID.randomUUID();
        followInsert(followId);

        mockMvc.perform(get("/api/follows/followed-by-me")
                .param("followeeId",followeeId.toString()))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.exceptionName").value("UnauthorizedException"))
                .andExpect(jsonPath("$.message").value("인증이 필요합니다."))
                .andExpect(jsonPath("$.details").isMap())
                .andExpect(jsonPath("$.details").isEmpty());
    }

    @Test
    @DisplayName("JWT 없이 팔로워 수를 조회하면 공통 오류 응답으로 401을 반환한다.")
    void getFollowCount_returnsUnauthorizedWithoutJwt() throws Exception {
        UUID followId = UUID.randomUUID();
        followInsert(followId);

        mockMvc.perform(get("/api/follows/count")
                .param("followeeId", followeeId.toString()))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.exceptionName").value("UnauthorizedException"))
                .andExpect(jsonPath("$.message").value("인증이 필요합니다."))
                .andExpect(jsonPath("$.details").isMap())
                .andExpect(jsonPath("$.details").isEmpty());
    }

    @Test
    @DisplayName("만료된 JWT로 팔로우를 등록하면 401을 반환하고 관계 저장과 생성 이벤트 발행이 발생하지 않는다.")
    void follow_returnsUnauthorizedWithExpiredJwt(ApplicationEvents applicationEvents) throws Exception {
        String expiredToken = createExpiredAccessToken(followerId);
        assertThatThrownBy(()-> jwtTokenProvider.parseAccessToken(expiredToken))
                .isInstanceOf(ExpiredJwtException.class);

        mockMvc.perform(post("/api/follows")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + expiredToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {
                            "followeeId": "%s"
                        }
                        """.formatted(followeeId)))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.exceptionName").value("UnauthorizedException"))
                .andExpect(jsonPath("$.message").value("인증이 필요합니다."))
                .andExpect(jsonPath("$.details").isMap())
                .andExpect(jsonPath("$.details").isEmpty());

        Long savedFollowCount = jdbcTemplate.queryForObject("""
                    SELECT COUNT(*)
                    FROM follows
                    WHERE followee_id = ?
                """, Long.class, followeeId);

        assertThat(savedFollowCount).isZero();
        assertThat(applicationEvents.stream(UserFollowedEvent.class))
                .isEmpty();
    }

    @Test
    @DisplayName("만료된 JWT로 팔로우를 취소하면 공통오류 응답으로 401을 반환하고 기존 관계를 유지한다.")
    void unfollow_returnsUnauthorizedWithExpiredJwt() throws Exception {
        UUID followId = UUID.randomUUID();
        followInsert(followId);

        String findRelationSql = """
                SELECT id, follower_id, followee_id, created_at
                FROM follows
                WHERE id =?
                """;

        List<Map<String, Object>> beforeRows = jdbcTemplate.queryForList(findRelationSql, followId);
        assertThat(beforeRows).hasSize(1);

        String expiredToken = createExpiredAccessToken(followerId);
        assertThatThrownBy(()-> jwtTokenProvider.parseAccessToken(expiredToken))
                .isInstanceOf(ExpiredJwtException.class);

        mockMvc.perform(delete("/api/follows/{followId}", followId)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + expiredToken))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.exceptionName").value("UnauthorizedException"))
                .andExpect(jsonPath("$.message").value("인증이 필요합니다."))
                .andExpect(jsonPath("$.details").isMap())
                .andExpect(jsonPath("$.details").isEmpty());

        List<Map<String, Object>> afterRows = jdbcTemplate.queryForList(findRelationSql, followId);
        assertThat(afterRows)
                .hasSize(1)
                .containsExactlyElementsOf(beforeRows);


    }

    @Test
    @DisplayName("만료된 JWT로 내 팔로우 관계를 조회하면 공통 오류 응답으로 401을 반환한다.")
    void getFollowedByMe_returnsUnauthorizedWithExpiredJwt() throws Exception {
        UUID followId = UUID.randomUUID();
        followInsert(followId);

        String expiredToken = createExpiredAccessToken(followerId);
        assertThatThrownBy(()-> jwtTokenProvider.parseAccessToken(expiredToken))
                .isInstanceOf(ExpiredJwtException.class);

        mockMvc.perform(get("/api/follows/followed-by-me")
                .param("followeeId", followeeId.toString())
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + expiredToken))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.exceptionName").value("UnauthorizedException"))
                .andExpect(jsonPath("$.message").value("인증이 필요합니다."))
                .andExpect(jsonPath("$.details").isMap())
                .andExpect(jsonPath("$.details").isEmpty());
    }

    @Test
    @DisplayName("만료된 JWT로 팔로워 수를 조회하면 공통 오류 응답으로 401을 반환한다.")
    void getFollowCount_returnsUnauthorizedWithExpiredJwt() throws Exception {
        UUID followId = UUID.randomUUID();
        followInsert(followId);

        String expiredToken = createExpiredAccessToken(followerId);
        assertThatThrownBy(()-> jwtTokenProvider.parseAccessToken(expiredToken))
                .isInstanceOf(ExpiredJwtException.class);

        mockMvc.perform(get("/api/follows/count")
                .param("followeeId", followeeId.toString())
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + expiredToken))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.exceptionName").value("UnauthorizedException"))
                .andExpect(jsonPath("$.message").value("인증이 필요합니다."))
                .andExpect(jsonPath("$.details").isMap())
                .andExpect(jsonPath("$.details").isEmpty());
    }

    @Test
    @DisplayName("변조된 JWT로 팔로우를 등록하면 401을 반환하고 관계 저장과 생성 이벤트 발행이 발생하지 않는다.")
    void follow_returnsUnauthorizedWithTamperedJwt(ApplicationEvents applicationEvents) throws Exception {
        String tamperedToken = createTamperedAccessToken(followerId);
        assertThatThrownBy(()-> jwtTokenProvider.parseAccessToken(tamperedToken))
                .isInstanceOf(SignatureException.class);

        mockMvc.perform(post("/api/follows")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + tamperedToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {
                            "followeeId": "%s"
                        }
                        """.formatted(followeeId)))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.exceptionName").value("UnauthorizedException"))
                .andExpect(jsonPath("$.message").value("인증이 필요합니다."))
                .andExpect(jsonPath("$.details").isMap())
                .andExpect(jsonPath("$.details").isEmpty());

        Long saveFollowCount = jdbcTemplate.queryForObject("""
                    SELECT COUNT(*)
                    FROM follows
                    WHERE followee_id = ?
                """, Long.class, followeeId);

        assertThat(saveFollowCount).isZero();
        assertThat(applicationEvents.stream(UserFollowedEvent.class))
                .isEmpty();
    }

    @Test
    @DisplayName("변조된 JWT로 팔로우를 취소하면 공통오류 응답으로 401을 반환하고 기존 관계를 유지한다.")
    void unfollow_returnsUnauthorizedWithTamperedJwt() throws Exception {
        UUID followId = UUID.randomUUID();
        followInsert(followId);

        String findRelationSql = """
                SELECT id, follower_id, followee_id, created_at
                FROM follows
                WHERE id = ?
                """;

        List<Map<String, Object>> beforeRows = jdbcTemplate.queryForList(findRelationSql, followId);
        assertThat(beforeRows).hasSize(1);

        String tamperedToken = createTamperedAccessToken(followerId);
        assertThatThrownBy(()-> jwtTokenProvider.parseAccessToken(tamperedToken))
                .isInstanceOf(SignatureException.class);

        mockMvc.perform(delete("/api/follows/{followId}", followId)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + tamperedToken))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.exceptionName").value("UnauthorizedException"))
                .andExpect(jsonPath("$.message").value("인증이 필요합니다."))
                .andExpect(jsonPath("$.details").isMap())
                .andExpect(jsonPath("$.details").isEmpty());

        List<Map<String, Object>> afterRows = jdbcTemplate.queryForList(findRelationSql, followId);
        assertThat(afterRows)
                .hasSize(1)
                .containsExactlyElementsOf(beforeRows);

    }

    @Test
    @DisplayName("변조된 JWT로 내 팔로우 관계를 조회하면 공통 오류 응답으로 401을 반환한다.")
    void getFollowedByMe_returnsUnauthorizedWithTamperedJwt() throws Exception {
        UUID followId = UUID.randomUUID();
        followInsert(followId);

        String tamperedToken = createTamperedAccessToken(followerId);
        assertThatThrownBy(()-> jwtTokenProvider.parseAccessToken(tamperedToken))
                .isInstanceOf(SignatureException.class);

        mockMvc.perform(get("/api/follows/followed-by-me")
                .param("followeeId", followeeId.toString())
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + tamperedToken))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.exceptionName").value("UnauthorizedException"))
                .andExpect(jsonPath("$.message").value("인증이 필요합니다."))
                .andExpect(jsonPath("$.details").isMap())
                .andExpect(jsonPath("$.details").isEmpty());
    }

    @Test
    @DisplayName("변조된 JWT로 팔로워 수를 조회하면 공통 오류 응답으로 401을 반환한다.")
    void getFollowCount_returnsUnauthorizedWithTamperedJwt() throws Exception {
        UUID followId = UUID.randomUUID();
        followInsert(followId);

        String tamperedToken = createTamperedAccessToken(followerId);
        assertThatThrownBy(()-> jwtTokenProvider.parseAccessToken(tamperedToken))
                .isInstanceOf(SignatureException.class);

        mockMvc.perform(get("/api/follows/count")
                .param("followeeId", followeeId.toString())
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + tamperedToken))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.exceptionName").value("UnauthorizedException"))
                .andExpect(jsonPath("$.message").value("인증이 필요합니다."))
                .andExpect(jsonPath("$.details").isMap())
                .andExpect(jsonPath("$.details").isEmpty());
    }

    @Test
    @DisplayName("유효한 JWT로 본인의 팔로우 관계를 조회하면 200과 정확한 관계 정보를 반환한다.")
    void getFollowedByMe_returnsOwnRelationWithValidJwt() throws Exception {
        UUID followId = UUID.randomUUID();
        followInsert(followId);

        String accessToken = jwtTokenProvider.createAccessToken(followerId);
        mockMvc.perform(get("/api/follows/followed-by-me")
                .param("followeeId", followeeId.toString())
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.id").value(followId.toString()))
                .andExpect(jsonPath("$.followerId").value(followerId.toString()))
                .andExpect(jsonPath("$.followeeId").value(followeeId.toString()));
    }

    @Test
    @DisplayName("같은 대상을 팔로우한 두 사용자가 각자의 JWT로 조회하면 각각 자신의 관계 정보를 반환한다.")
    void getFollowedByMe_returnsEachAuthenticatedUsersRelation() throws Exception {
        UUID firstFollowId = UUID.randomUUID();
        followInsert(firstFollowId);

        UUID secondFollowId = UUID.randomUUID();
        followInsert(secondFollowId, otherFollowerId, followeeId);

        String firstToken = jwtTokenProvider.createAccessToken(followerId);
        String secondToken = jwtTokenProvider.createAccessToken(otherFollowerId);

        mockMvc.perform(get("/api/follows/followed-by-me")
                .param("followeeId", followeeId.toString())
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + firstToken))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.id").value(firstFollowId.toString()))
                .andExpect(jsonPath("$.followerId").value(followerId.toString()))
                .andExpect(jsonPath("$.followeeId").value(followeeId.toString()));


        mockMvc.perform(get("/api/follows/followed-by-me")
                        .param("followeeId", followeeId.toString())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + secondToken))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.id").value(secondFollowId.toString()))
                .andExpect(jsonPath("$.followerId").value(otherFollowerId.toString()))
                .andExpect(jsonPath("$.followeeId").value(followeeId.toString()));


    }

    @Test
    @DisplayName("유효한 JWT로 팔로우하지 않은 사용자를 조회하면 404와 공통 오류 응답을 반환한다.")
    void getFollowedByMe_returnsNotFoundWhenNotFollowing() throws Exception {
        String accessToken = jwtTokenProvider.createAccessToken(followerId);

        mockMvc.perform(get("/api/follows/followed-by-me")
                .param("followeeId", followeeId.toString())
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.exceptionName").value("FollowNotFoundException"))
                .andExpect(jsonPath("$.message").value("팔로우를 찾을 수 없습니다."))
                .andExpect(jsonPath("$.details").isMap())
                .andExpect(jsonPath("$.details.followeeId").value(followeeId.toString()));


    }

    @Test
    @DisplayName("유효한 JWT로 팔로우를 취소한 뒤 관계를 조회하면 404와 공통 오류 응답을 반환한다.")
    void getFollowedByMe_returnsNotFoundAfterUnfollow() throws Exception {
        UUID followId = UUID.randomUUID();
        followInsert(followId);

        String accessToken = jwtTokenProvider.createAccessToken(followerId);
        mockMvc.perform(delete("/api/follows/{followId}", followId)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));

        mockMvc.perform(get("/api/follows/followed-by-me")
                .param("followeeId", followeeId.toString())
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.exceptionName").value("FollowNotFoundException"))
                .andExpect(jsonPath("$.message").value("팔로우를 찾을 수 없습니다."))
                .andExpect(jsonPath("$.details").isMap())
                .andExpect(jsonPath("$.details.followeeId").value(followeeId.toString()));


    }

    @Test
    @DisplayName("유효한 JWT로 존재하지 않는 사용자의 팔로우 관계를 조회하면 404와 공통 오류 응답을 반환한다.")
    void getFollowedByMe_returnsNotFoundForMissingFollowee() throws Exception {
        UUID missingFolloweeId = UUID.randomUUID();
        String accessToken = jwtTokenProvider.createAccessToken(followerId);

        mockMvc.perform(get("/api/follows/followed-by-me")
                .param("followeeId", missingFolloweeId.toString())
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.exceptionName").value("FollowNotFoundException"))
                .andExpect(jsonPath("$.message").value("팔로우를 찾을 수 없습니다."))
                .andExpect(jsonPath("$.details").isMap())
                .andExpect(jsonPath("$.details.followeeId").value(missingFolloweeId.toString()));
    }

    @Test
    @DisplayName("다른 사용자의 팔로우 관계만 존재하면 내 관계 조회는 404를 반환하고 기존 관계를 유지한다.")
    void getFollowedByMe_returnsNotFoundWhenOnlyAnotherUserFollows() throws Exception {
        UUID otherFollowId = UUID.randomUUID();
        followInsert(otherFollowId, otherFollowerId, followeeId);

        String accessToken = jwtTokenProvider.createAccessToken(followerId);
        mockMvc.perform(get("/api/follows/followed-by-me")
                .param("followeeId", followeeId.toString())
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.exceptionName").value("FollowNotFoundException"))
                .andExpect(jsonPath("$.message").value("팔로우를 찾을 수 없습니다."))
                .andExpect(jsonPath("$.details").isMap())
                .andExpect(jsonPath("$.details.followeeId").value(followeeId.toString()));

        Long remainingCount = jdbcTemplate.queryForObject("""
                    SELECT COUNT(*)
                    FROM follows
                    WHERE id = ? AND follower_id = ?
                        AND followee_id = ?
                """, Long.class, otherFollowId, otherFollowerId, followeeId);

        assertThat(remainingCount).isEqualTo(1L);
    }

    @Test
    @DisplayName("유효한 JWT로 팔로워가 없는 사용자의 팔로워 수를 조회하면 200과 숫자 0을 반환한다.")
    void getFollowCount_returnsZeroWhenNoFollowers() throws Exception {
        String accessToken = jwtTokenProvider.createAccessToken(followerId);

        mockMvc.perform(get("/api/follows/count")
                .param("followeeId", followeeId.toString())
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(content().string("0"));
    }

    @Test
    @DisplayName("일반 사용자 JWT로 다른 사용자의 팔로워 수를 조회하면 해당 사용자의 팔로워만 집계한다.")
    void getFollowCount_returnsTargetFollowerCountWithUserJwt() throws Exception {
        followInsert(UUID.randomUUID(), followerId, followeeId);
        followInsert(UUID.randomUUID(), otherFollowerId, followeeId);

        followInsert(UUID.randomUUID(), followeeId, followerId);

        String accessToken = jwtTokenProvider.createAccessToken(followerId);

        mockMvc.perform(get("/api/follows/count")
                .param("followeeId", followeeId.toString())
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(content().string("2"));
    }

    @Test
    @DisplayName("유효한 JWT로 존재하지 않는 사용자의 팔로워 수를 조회하면 200과 숫자 0을 반환한다.")
    void getFollowCount_returnsZeroForMissingFollowee() throws Exception {
        UUID missingFolloweeId = UUID.randomUUID();
        String accessToken = jwtTokenProvider.createAccessToken(followerId);

        mockMvc.perform(get("/api/follows/count")
                .param("followeeId", missingFolloweeId.toString())
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(content().string("0"));
    }

    @Test
    @DisplayName("유효한 JWT로 내 팔로우 관계 조회시 대상 ID를 누락하면 400과 공통 오류 응답을 반환한다.")
    void getFollowedByMe_returnsBadRequestWhenFolloweeIdMissing() throws Exception {
        String accessToken = jwtTokenProvider.createAccessToken(followerId);

        mockMvc.perform(get("/api/follows/followed-by-me")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.exceptionName").value(MethodArgumentNotValidException.class.getSimpleName()))
                .andExpect(jsonPath("$.message").value("요청 데이터가 올바르지 않습니다."))
                .andExpect(jsonPath("$.details").isMap())
                .andExpect(jsonPath("$.details.followeeId").isArray())
                .andExpect(jsonPath("$.details.followeeId").isNotEmpty());
    }

    @Test
    @DisplayName("유효한 JWT로 내 팔로우 관계 조회시 대상 ID의 UUID 형식이 잘못되면 400과 공통 오류 응답을 반환한다.")
    void getFollowedByMe_returnsBadRequestWhenFolloweeIdInvalid() throws Exception {
        String accessToken = jwtTokenProvider.createAccessToken(followerId);

        mockMvc.perform(get("/api/follows/followed-by-me")
                .param("followeeId", "not-a-uuid")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.exceptionName").value(MethodArgumentNotValidException.class.getSimpleName()))
                .andExpect(jsonPath("$.message").value("요청 데이터가 올바르지 않습니다."))
                .andExpect(jsonPath("$.details").isMap())
                .andExpect(jsonPath("$.details.followeeId").isArray())
                .andExpect(jsonPath("$.details.followeeId").isNotEmpty());
    }

    @Test
    @DisplayName("유효한 JWT로 팔로워 수 조회 시 대상 ID를 누락하면 400과 공통 오류 응답을 반환한다.")
    void getFollowCount_returnsBadRequestWhenFolloweeIdMissing() throws Exception {
        String accessToken = jwtTokenProvider.createAccessToken(followerId);

        mockMvc.perform(get("/api/follows/count")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.exceptionName").value(MethodArgumentNotValidException.class.getSimpleName()))
                .andExpect(jsonPath("$.message").value("요청 데이터가 올바르지 않습니다."))
                .andExpect(jsonPath("$.details").isMap())
                .andExpect(jsonPath("$.details.followeeId").isArray())
                .andExpect(jsonPath("$.details.followeeId").isNotEmpty());
    }

    @Test
    @DisplayName("유효한 JWT로 팔로워 수 조회 시 대상 ID의 UUID 형식이 잘못되면 400과 공통 오류 응답을 반환한다.")
    void getFollowCount_returnsBadRequestWhenFolloweeIdInvalid() throws Exception {
        String accessToken = jwtTokenProvider.createAccessToken(followerId);

        mockMvc.perform(get("/api/follows/count")
                .param("followeeId", "not-a-uuid")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.exceptionName").value(MethodArgumentNotValidException.class.getSimpleName()))
                .andExpect(jsonPath("$.message").value("요청 데이터가 올바르지 않습니다."))
                .andExpect(jsonPath("$.details").isMap())
                .andExpect(jsonPath("$.details.followeeId").isArray())
                .andExpect(jsonPath("$.details.followeeId").isNotEmpty());
    }

    /**
     * 두 HTTP 요청의 결과와 최종 DB 상태를 확인한다. 두 사전 조회가 모두 빈 결과가 되도록 강제하지는 않는다.
     * 실제 UNIQUE 충돌 후 복구 경로는 FollowServiceIntegrationTest에서 별도로 검증한다.
     */
    @Test
    @DisplayName("유효한 JWT로 같은 대상을 동시에 팔로우하면 관계 한 건만 저장하고 201과 200 응답에 동일한 관계 ID를 반환한다.")
    void follow_returnsCreatedAndOkForConcurrentRequests() throws Exception {
        String accessToken = jwtTokenProvider.createAccessToken(followerId);

        String requestBody = """
                {
                    "followeeId": "%s"
                }
                """.formatted(followeeId);

        // 두 작업이 준비된 뒤 함께 출발시킨다. 대기 제한은 테스트의 무한 대기를 막기 위한 값이다.
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);

        Callable<ResultActions> request = () ->{
            ready.countDown();

            if (!start.await(10, TimeUnit.SECONDS)){
                throw new IllegalStateException("동시 요청 시작 대기 시간 초과");
            }
            return mockMvc.perform(post("/api/follows")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(requestBody));
        };

        ExecutorService executor = Executors.newFixedThreadPool(2);

        try {
            Future<ResultActions> firstFuture = executor.submit(request);
            Future<ResultActions> secondFuture = executor.submit(request);

            assertThat(ready.await(10, TimeUnit.SECONDS))
                    .as("두 요청이 실행 준비를 마쳐야 한다.")
                    .isTrue();

            start.countDown();
            ResultActions first = firstFuture.get(20, TimeUnit.SECONDS);
            ResultActions second = secondFuture.get(20, TimeUnit.SECONDS);

            int firstStatus = first.andReturn().getResponse().getStatus();
            int secondStatus = second.andReturn().getResponse().getStatus();
            assertThat(List.of(firstStatus, secondStatus))
                    .containsExactlyInAnyOrder(201, 200);

            List<UUID> savedFollowIds = jdbcTemplate.queryForList("""
                        SELECT id
                        FROM follows
                        WHERE follower_id =?
                            AND followee_id =?
                    """, UUID.class, followerId, followeeId);

            assertThat(savedFollowIds).hasSize(1);
            String savedFollowId = savedFollowIds.get(0).toString();

            first.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                    .andExpect(jsonPath("$.id").value(savedFollowId));

            second.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                    .andExpect(jsonPath("$.id").value(savedFollowId));
        } finally {
            // 준비 단계에서 실패해도 대기 중인 작업을 풀고 실행기를 종료해 다음 테스트에 스레드를 남기지 않는다.
            start.countDown();
            executor.shutdownNow();
            assertThat(executor.awaitTermination(10, TimeUnit.SECONDS))
                    .as("테스트 작업 스레드가 종료되어야 한다.")
                    .isTrue();
        }
    }

    private void insertUser(UUID userId, String name){
        jdbcTemplate.update("""
            INSERT INTO users(id, email, nickname, role, enabled, created_at)
            VALUES (?, ?, ?, 'USER', true, CURRENT_TIMESTAMP)
        """,
                userId,
                userId + "@test.example",
                name);
    }

    /**
     * 동일한 서명 키와 유효기간을 사용하되 발급 시각을 과거로 옮겨, 기다리지 않고 만료 토큰을 만든다.
     * 각 테스트는 HTTP 요청 전에 ExpiredJwtException을 확인해 의도한 실패 원인인지 먼저 검증한다.
     */
    private String createExpiredAccessToken(UUID userId){
        SecretKey signingKey = Keys.hmacShaKeyFor(Decoders.BASE64.decode(jwtProperties.secretKey()));

        Duration validity = jwtProperties.accessTokenValidity();

        Instant issuedAt = Instant.now()
                .minus(validity)
                .minus(Duration.ofMinutes(1));

        JwtTokenProvider pastTokenProvider = new JwtTokenProvider(signingKey, validity,
                jwtProperties.refreshTokenValidity(), Clock.fixed(issuedAt, ZoneOffset.UTC));
        return pastTokenProvider.createAccessToken(userId);

    }

    private void followInsert(UUID followId) {
        followInsert(followId, followerId, followeeId);
    }

    // 조회·취소 검증의 사전 관계를 직접 준비한다. 서비스 등록과 생성 이벤트 발행은 실행하지 않는다.
    private void followInsert(UUID followId, UUID followerId, UUID followeeId) {
        jdbcTemplate.update("""
            INSERT INTO follows (id, follower_id, followee_id, created_at)
            VALUES (?, ?, ?, CURRENT_TIMESTAMP)
        """, followId, followerId, followeeId);
    }

    /**
     * 정상 토큰의 헤더·페이로드를 유지하고 서명 첫 글자만 바꾼다.
     * 각 테스트는 SignatureException을 먼저 확인해 토큰 형식이나 만료 오류와 구분한다.
     */
    private String createTamperedAccessToken(UUID userId){
        String validToken = jwtTokenProvider.createAccessToken(userId);
        String[] tokenParts = validToken.split("\\.");

        String signature = tokenParts[2];
        String replacement = signature.startsWith("A") ? "B" : "A";
        tokenParts[2] = replacement + signature.substring(1);

        return String.join(".", tokenParts);
    }

    /**
     * 이 클래스의 MockMvc 요청에서만 CSRF 검사를 제외한다.
     * JWT 인증과 소유자 인가는 계속 검증한다.
     *
     * TODO(#114): CSRF 적용 후 실제 토큰을 준비하도록 테스트를 변경하고,
     * 이 설정 클래스와 이를 연결한 @Import를 제거한다.
     */
    @TestConfiguration(proxyBeanMethods = false)
    static class CsrfDisabledTestConfig{
        @Bean
        MockMvcBuilderCustomizer disableCsrfForFollowSecurityTests(){
            return builder -> builder
                    .defaultRequest(get("/").with(request ->{
                        CsrfFilter.skipRequest(request);
                        return request;
                    }));
        }
    }
}
