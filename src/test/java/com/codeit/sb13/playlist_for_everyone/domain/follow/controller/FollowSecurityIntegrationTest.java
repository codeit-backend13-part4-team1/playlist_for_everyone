package com.codeit.sb13.playlist_for_everyone.domain.follow.controller;

import com.codeit.sb13.playlist_for_everyone.domain.auth.jwt.JwtProperties;
import com.codeit.sb13.playlist_for_everyone.domain.auth.jwt.JwtTokenProvider;
import com.codeit.sb13.playlist_for_everyone.domain.follow.event.UserFollowedEvent;
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
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
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

@RecordApplicationEvents
@SpringBootTest(
        properties = {
                "spring.flyway.enabled=true",
                "spring.jpa.hibernate.ddl-auto=validate",
                "spring.docker.compose.enabled=false"
        }
)
@AutoConfigureMockMvc
@Testcontainers
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@DisplayName("팔로우 JWT 인증·인가 통합 테스트")
public class FollowSecurityIntegrationTest {

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
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private JwtProperties jwtProperties;

    private final UUID followerId = UUID.randomUUID();

    private final UUID followeeId = UUID.randomUUID();

    private final UUID otherFollowerId = UUID.randomUUID();

    @BeforeEach
    void setUp(){
        insertUser(followerId, "팔로우하는 사용자");
        insertUser(followeeId, "팔로우받는 사용자");
        insertUser(otherFollowerId, "다른 팔로우하는 사용자");
    }

    @AfterEach
    void cleanUp(){
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

    @Test
    @DisplayName("유효한 JWT로 같은 대상을 동시에 팔로우하면 관계 한 건만 저장하고 201과 200 응답에 동일한 관계 ID를 반환한다.")
    void follow_returnsCreatedAndOkForConcurrentRequests() throws Exception {
        String accessToken = jwtTokenProvider.createAccessToken(followerId);

        String requestBody = """
                {
                    "followeeId": "%s"
                }
                """.formatted(followeeId);

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

    private void followInsert(UUID followId, UUID followerId, UUID followeeId) {
        jdbcTemplate.update("""
            INSERT INTO follows (id, follower_id, followee_id, created_at)
            VALUES (?, ?, ?, CURRENT_TIMESTAMP)
        """, followId, followerId, followeeId);
    }

    private String createTamperedAccessToken(UUID userId){
        String validToken = jwtTokenProvider.createAccessToken(userId);
        String[] tokenParts = validToken.split("\\.");

        String signature = tokenParts[2];
        String replacement = signature.startsWith("A") ? "B" : "A";
        tokenParts[2] = replacement + signature.substring(1);

        return String.join(".", tokenParts);
    }
}
