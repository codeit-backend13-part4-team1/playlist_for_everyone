package com.codeit.sb13.playlist_for_everyone.domain.conversation.controller;

import com.codeit.sb13.playlist_for_everyone.domain.auth.jwt.JwtTokenProvider;
import com.codeit.sb13.playlist_for_everyone.domain.auth.repository.UserSessionRepository;
import jakarta.servlet.http.Cookie;
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
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
@DisplayName("대화방 생성 JWT 인증·CSRF 통합 테스트")
public class ConversationSecurityIntegrationTest {

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

    @MockitoBean
    private UserSessionRepository userSessionRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private final UUID requesterId = UUID.randomUUID();
    private final UUID withUserId = UUID.randomUUID();
    private final String CSRF_TOKEN_HEADER = "X-XSRF-TOKEN";
    private final String UNAUTHORIZED_EXCEPTION = "UnauthorizedException";
    private final String UNAUTHORIZED_EXCEPTION_MESSAGE = "인증이 필요합니다.";
    private final String BEARER_PREFIX = "Bearer ";

    @BeforeEach
    void setUp() {
        insertUser(requesterId, "요청자");
        insertUser(withUserId, "대화 상대");
    }

    @AfterEach
    void cleanUp() {

        jdbcTemplate.update("""
              DELETE FROM conversations
              WHERE user_a_id IN (?, ?)
                 OR user_b_id IN (?, ?)
              """,
                requesterId, withUserId,
                requesterId, withUserId
        );

        jdbcTemplate.update("""
              DELETE FROM users
              WHERE id IN (?, ?)
              """, requesterId, withUserId
        );
    }

    @Test
    @DisplayName("유효한 JWT와 정상 CSRF로 요청하면 인증 사용자의 대화방을 저장하고 200을 반환한다.")
    void create_returnsOkWithValidJwtAndCsrf() throws Exception {
        String accessToken = issueAccessToken();
        Cookie csrfCookie = issueCsrfCookie();

        ResultActions result = mockMvc.perform(post("/api/conversations")
                        .header(HttpHeaders.AUTHORIZATION, BEARER_PREFIX + accessToken)
                        .cookie(csrfCookie)
                        .header(CSRF_TOKEN_HEADER, csrfCookie.getValue())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "withUserId": "%s"
                                }
                                """.formatted(withUserId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.with.userId").value(withUserId.toString()));

        List<UUID> savedIds = jdbcTemplate.queryForList("""
                            SELECT id
                            FROM conversations
                            WHERE (user_a_id = ? AND user_b_id = ?)
                                OR (user_a_id = ? AND user_b_id = ?)
                        """, UUID.class,
                requesterId, withUserId,
                withUserId, requesterId);

        assertThat(savedIds).hasSize(1);
        result.andExpect(jsonPath("$.id").value(savedIds.get(0).toString()));
    }

    @Test
    @DisplayName("정상 CSRF를 전달해도 JWT가 없으면 401을 반환하고 대화방을 저장하지 않는다.")
    void create_returnsUnauthorizedWhenJwtMissing() throws Exception {
        Cookie csrfCookie = issueCsrfCookie();

        mockMvc.perform(post("/api/conversations")
                .cookie(csrfCookie)
                .header(CSRF_TOKEN_HEADER, csrfCookie.getValue())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                                {
                                    "withUserId": "%s"
                                }
                                """.formatted(withUserId)))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.exceptionName").value(UNAUTHORIZED_EXCEPTION))
                .andExpect(jsonPath("$.message").value(UNAUTHORIZED_EXCEPTION_MESSAGE))
                .andExpect(jsonPath("$.details").isMap())
                .andExpect(jsonPath("$.details").isEmpty());

        assertThat(countConversationsBetweenUsers()).isZero();
    }

    @Test
    @DisplayName("정상 CSRF를 전달해도 JWT 형식이 잘못되면 401을 반환하고 대화방을 저장하지 않는다.")
    void create_returnsUnauthorizedWhenJwtInvalid() throws Exception {
        Cookie csrfCookie = issueCsrfCookie();
        mockMvc.perform(post("/api/conversations")
                .header(HttpHeaders.AUTHORIZATION, BEARER_PREFIX + "invalid-token")
                .cookie(csrfCookie)
                .header(CSRF_TOKEN_HEADER, csrfCookie.getValue())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                                {
                                    "withUserId": "%s"
                                }
                                """.formatted(withUserId)))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.exceptionName").value(UNAUTHORIZED_EXCEPTION))
                .andExpect(jsonPath("$.message").value(UNAUTHORIZED_EXCEPTION_MESSAGE))
                .andExpect(jsonPath("$.details").isMap())
                .andExpect(jsonPath("$.details").isEmpty());

        assertThat(countConversationsBetweenUsers()).isZero();
    }

    @Test
    @DisplayName("유효한 JWT를 전달해도 CSRF 토큰이 없으면 403을 반환하고 대화방을 저장하지 않는다.")
    void create_returnsForbiddenWhenCsrfMissing() throws Exception {
        String accessToken = issueAccessToken();
        mockMvc.perform(post("/api/conversations")
                .header(HttpHeaders.AUTHORIZATION, BEARER_PREFIX + accessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                                {
                                    "withUserId": "%s"
                                }
                                """.formatted(withUserId)))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.exceptionName").value(AccessDeniedException.class.getSimpleName()))
                .andExpect(jsonPath("$.message").value("접근 권한이 없습니다."))
                .andExpect(jsonPath("$.details").isMap())
                .andExpect(jsonPath("$.details").isEmpty());

        assertThat(countConversationsBetweenUsers()).isZero();
    }

    @Test
    @DisplayName("유효한 JWT를 전달해도 CSRF 쿠키와 헤더가 다르면 403을 반환하고 대화방을 저장하지 않는다.")
    void create_returnsForbiddenWhenCsrfMismatch() throws Exception {
        String accessToken = issueAccessToken();
        Cookie csrfCookie = issueCsrfCookie();
        String differentCsrfToken = csrfCookie.getValue() + "-changed";

        mockMvc.perform(post("/api/conversations")
                .header(HttpHeaders.AUTHORIZATION, BEARER_PREFIX + accessToken)
                .cookie(csrfCookie)
                .header(CSRF_TOKEN_HEADER, differentCsrfToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                                {
                                    "withUserId": "%s"
                                }
                                """.formatted(withUserId)))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.exceptionName").value(AccessDeniedException.class.getSimpleName()))
                .andExpect(jsonPath("$.message").value("접근 권한이 없습니다."))
                .andExpect(jsonPath("$.details").isMap())
                .andExpect(jsonPath("$.details").isEmpty());

        assertThat(countConversationsBetweenUsers()).isZero();
    }

    private void insertUser(UUID userId, String name) {
        jdbcTemplate.update("""
                  INSERT INTO users ( id, email, nickname, role, enabled, created_at )
                  VALUES (?, ?, ?, 'USER', true, CURRENT_TIMESTAMP)
              """, userId, userId + "@test.example", name);
    }

    private String issueAccessToken() {
        UUID sessionId = UUID.randomUUID();

        given(userSessionRepository.isCurrent(requesterId, sessionId)).willReturn(true);

        return jwtTokenProvider.createAccessToken(requesterId, sessionId);
    }

    private Cookie issueCsrfCookie() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/auth/csrf-token"))
                        .andExpect(status().isNoContent())
                        .andReturn();

        Cookie csrfCookie = result.getResponse().getCookie("XSRF-TOKEN");

        assertThat(csrfCookie).isNotNull();
        assertThat(csrfCookie.getValue()).isNotBlank();

        return csrfCookie;
    }

    private Long countConversationsBetweenUsers() {
        return jdbcTemplate.queryForObject("""
                        SELECT COUNT(*)
                        FROM conversations
                        WHERE user_a_id IN (?, ?)
                            OR user_b_id IN (?, ?)
                        """, Long.class,
                requesterId, withUserId,
                requesterId, withUserId);
    }
}
