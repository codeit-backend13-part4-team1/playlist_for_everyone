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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
