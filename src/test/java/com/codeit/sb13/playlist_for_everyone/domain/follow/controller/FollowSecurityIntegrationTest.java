package com.codeit.sb13.playlist_for_everyone.domain.follow.controller;

import com.codeit.sb13.playlist_for_everyone.domain.auth.jwt.JwtTokenProvider;
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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

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

    private final UUID followerId = UUID.randomUUID();

    private final UUID followeeId = UUID.randomUUID();

    @BeforeEach
    void setUp(){
        insertUser(followerId, "팔로우하는 사용자");
        insertUser(followeeId, "팔로우받는 사용자");
    }

    @AfterEach
    void cleanUp(){
        jdbcTemplate.update("""
            DELETE FROM follows
            WHERE follower_id IN (?, ?)
                OR followee_id IN (?, ?)
        """, followeeId, followerId,
                followeeId, followerId);
        jdbcTemplate.update("DELETE FROM users WHERE id IN (?, ?)", followeeId, followerId);
    }

    @Test
    @DisplayName("유효한 JWT로 신규 팔로우를 요청하면 관계를 저장하고 201과 동일한 관계 정보를 반환한다.")
    void follow_returnsCreatedWithAuthenticatedUser() throws Exception {
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
        jdbcTemplate.update("""
            INSERT INTO follows (id, follower_id, followee_id, created_at)
            VALUES (?, ?, ?, CURRENT_TIMESTAMP)
        """, followId, followerId, followeeId);

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
        jdbcTemplate.update("""
            INSERT INTO follows (id, follower_id, followee_id, created_at)
            VALUES (?, ?, ?, CURRENT_TIMESTAMP)
        """, followId, followerId, followeeId);

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
        jdbcTemplate.update("""
            INSERT INTO follows (id, follower_id, followee_id, created_at)
            VALUES (?, ?, ?, CURRENT_TIMESTAMP)
        """, followId, followerId, followeeId);

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
        jdbcTemplate.update("""
            INSERT INTO follows (id, follower_id, followee_id, created_at)
            VALUES (?, ?, ?, CURRENT_TIMESTAMP)
        """, existingFollowId, followerId, followeeId);

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
    @DisplayName("JWT 없이 팔로우를 등록하면 공통 오류 응답으로 401을 반환하고 관계를 저장하지 않는다.")
    void follow_returnsUnauthorizedWithoutJwt() throws Exception {
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
    }

    @Test
    @DisplayName("JWT 없이 팔로우를 취소하면 공통 오류 응답으로 401을 반환하고 기존 관계를 유지한다.")
    void unfollow_returnsUnauthorizedWithoutJwt() throws Exception {
        UUID followId = UUID.randomUUID();
        jdbcTemplate.update("""
            INSERT INTO follows (id, follower_id, followee_id, created_at)
            VALUES (?, ?, ?, CURRENT_TIMESTAMP)
        """, followId, followerId, followeeId);

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
        jdbcTemplate.update("""
            INSERT INTO follows (id, follower_id, followee_id, created_at)
            VALUES (?, ?, ?, CURRENT_TIMESTAMP)
        """, followId, followerId, followeeId);

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
        jdbcTemplate.update("""
            INSERT INTO follows (id, follower_id, followee_id, created_at)
            VALUES (?, ?, ?, CURRENT_TIMESTAMP)
        """, followId, followerId, followeeId);

        mockMvc.perform(get("/api/follows/count")
                .param("followeeId", followeeId.toString()))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.exceptionName").value("UnauthorizedException"))
                .andExpect(jsonPath("$.message").value("인증이 필요합니다."))
                .andExpect(jsonPath("$.details").isMap())
                .andExpect(jsonPath("$.details").isEmpty());
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


}
