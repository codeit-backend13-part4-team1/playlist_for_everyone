package com.codeit.sb13.playlist_for_everyone.domain.conversation.controller;

import com.codeit.sb13.playlist_for_everyone.domain.auth.jwt.JwtTokenProvider;
import com.codeit.sb13.playlist_for_everyone.domain.auth.repository.UserSessionRepository;
import com.codeit.sb13.playlist_for_everyone.global.exception.ApiErrorCode;
import com.codeit.sb13.playlist_for_everyone.global.exception.conversation.ConversationNotFoundException;
import com.codeit.sb13.playlist_for_everyone.global.exception.conversation.SelfConversationNotAllowedException;
import com.codeit.sb13.playlist_for_everyone.global.file.storage.FileStorage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(
        properties = {
                "spring.flyway.enabled=true",
                "spring.jpa.hibernate.ddl-auto=validate",
                "spring.jpa.open-in-view=false",
                "spring.docker.compose.enabled=false"
        }
)
@AutoConfigureMockMvc
@Testcontainers
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@DisplayName("대화방 조회 JWT 인증·DB 통합 테스트")
public class ConversationQuerySecurityIntegrationTest {

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

    @MockitoBean
    private UserSessionRepository userSessionRepository;

    @MockitoBean
    private FileStorage fileStorage;

    private final UUID requesterId = UUID.randomUUID();
    private final UUID withUserId = UUID.randomUUID();
    private final UUID outsiderId = UUID.randomUUID();
    private final UUID conversationId = UUID.randomUUID();
    private final UUID messageId = UUID.randomUUID();
    private final UUID requesterProfileImageId = UUID.randomUUID();
    private final UUID withUserProfileImageId = UUID.randomUUID();
    private final String requesterProfileImageKey = "profiles/" + requesterProfileImageId + ".png";
    private final String withUserProfileImageKey = "profiles/" + withUserProfileImageId + ".png";
    private final String IMAGE_URL_PREFIX = "https://example.com/";
    public  final String BEARER_PREFIX = "Bearer ";

    @BeforeEach
    void setUp() {
        insertUser(requesterId, "요청자");
        insertUser(withUserId, "대화 상대");
        insertUser(outsiderId, "비참여자");

        insertProfileImage(
                requesterId,
                requesterProfileImageId,
                requesterProfileImageKey
        );
        insertProfileImage(
                withUserId,
                withUserProfileImageId,
                withUserProfileImageKey
        );

        insertConversation(conversationId, requesterId, withUserId);
        insertUnreadMessage();
    }

    @AfterEach
    void cleanUp() {
        jdbcTemplate.update("""
              DELETE FROM direct_messages
              WHERE conversation_id IN (
                  SELECT id
                  FROM conversations
                  WHERE user_a_id IN (?, ?, ?)
                     OR user_b_id IN (?, ?, ?)
              )
              """,
                requesterId, withUserId, outsiderId,
                requesterId, withUserId, outsiderId
        );

        jdbcTemplate.update("""
              DELETE FROM conversations
              WHERE user_a_id IN (?, ?, ?)
                 OR user_b_id IN (?, ?, ?)
              """,
                requesterId, withUserId, outsiderId,
                requesterId, withUserId, outsiderId
        );

        jdbcTemplate.update("""
              DELETE FROM users
              WHERE id IN (?, ?, ?)
              """,
                requesterId, withUserId, outsiderId
        );

        jdbcTemplate.update("""
          DELETE FROM file_infos
          WHERE id IN (?, ?)
          """, requesterProfileImageId, withUserProfileImageId);
    }

    @ParameterizedTest(name = "조회 유형={0}, 요청자 기준={1}")
    @CsvSource({
            "LIST, true",
            "LIST, false",
            "DETAIL, true",
            "DETAIL, false",
            "WITH, true",
            "WITH, false"
    })
    @DisplayName("두 참여자는 유효한 JWT로 CSRF 토큰 없이 대화방을 조회할 수 있다.")
    void query_returnsOkWithValidJwtWithoutCsrf(String queryType, boolean asRequester) throws Exception {
        UUID authenticateUserId = asRequester ? requesterId : withUserId;
        UUID otherUserId = asRequester ? withUserId : requesterId;
        String accessToken = issueAccessToken(authenticateUserId);

        MockHttpServletRequestBuilder request = switch (queryType){
            case "LIST" -> get("/api/conversations")
                    .param("limit", "10")
                    .param("sortBy", "createdAt")
                    .param("sortDirection", "DESCENDING");
            case "DETAIL" -> get("/api/conversations/{conversationId}", conversationId);
            case "WITH" -> get("/api/conversations/with")
                    .param("userId", otherUserId.toString());
            default -> throw new IllegalArgumentException("지원하지 않는 조회 유형입니다.");
        };

        given(fileStorage.getFileUrl(anyString()))
                .willAnswer(invocation -> {
                    assertThat(TransactionSynchronizationManager.isActualTransactionActive())
                            .as("프로필 이미지 URL은 조회 트랜잭션 밖에서 생성해야 한다.")
                            .isFalse();

                    String imageKey = invocation.getArgument(0);
                    return IMAGE_URL_PREFIX  + imageKey;
                });

        String otherProfileImageKey = asRequester ? withUserProfileImageKey
                : requesterProfileImageKey;

        String conversationPath = "LIST".equals(queryType)? "$.data[0]": "$";
        mockMvc.perform(request
                .header(HttpHeaders.AUTHORIZATION, BEARER_PREFIX + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath(conversationPath + ".id").value(conversationId.toString()))
                .andExpect(jsonPath(conversationPath + ".with.userId").value(otherUserId.toString()))
                .andExpect(jsonPath(conversationPath + ".with.profileImageUrl")
                        .value(IMAGE_URL_PREFIX + otherProfileImageKey))
                .andExpect(jsonPath(conversationPath + ".lastestMessage.id")
                        .value(messageId.toString()))
                .andExpect(jsonPath(conversationPath + ".lastestMessage.conversationId")
                        .value(conversationId.toString()))
                .andExpect(jsonPath(conversationPath + ".lastestMessage.sender.userId")
                        .value(withUserId.toString()))
                .andExpect(jsonPath(conversationPath + ".lastestMessage.sender.profileImageUrl")
                        .value(IMAGE_URL_PREFIX + withUserProfileImageKey))
                .andExpect(jsonPath(conversationPath + ".lastestMessage.receiver.userId")
                        .value(requesterId.toString()))
                .andExpect(jsonPath(conversationPath + ".lastestMessage.receiver.profileImageUrl")
                        .value(IMAGE_URL_PREFIX + requesterProfileImageKey))
                .andExpect(jsonPath(conversationPath + ".lastestMessage.content")
                        .value("안녕하세요."))
                .andExpect(jsonPath(conversationPath + ".hasUnread").value(asRequester));


        assertConversationCountAndReadStateUnchanged();
    }

    @ParameterizedTest(name = "실패 상황={0}, 응답 상태={1}")
    @CsvSource({
            "DETAIL_MISSING, 404",
            "DETAIL_NOT_PARTICIPANT, 404",
            "WITH_MISSING_USER, 404",
            "WITH_NO_CONVERSATION, 404",
            "WITH_SELF, 400"
    })
    @DisplayName("대화방 미존재·비참여 조회는 404, 자기 자신과의 대화방 조회는 400을 반환한다.")
    void query_returnsExpectedErrorWhenConversationUnavailable(String  scenario, int expectedStatus) throws Exception {
        UUID authenticatedUserId = "DETAIL_NOT_PARTICIPANT".equals(scenario) ? outsiderId : requesterId;
        String accessToken = issueAccessToken(authenticatedUserId);

        MockHttpServletRequestBuilder request = switch (scenario){
            case "DETAIL_MISSING" -> get("/api/conversations/{conversationId}", UUID.randomUUID());
            case "DETAIL_NOT_PARTICIPANT" -> get("/api/conversations/{conversationId}", conversationId);
            case "WITH_MISSING_USER" -> get("/api/conversations/with").param("userId", UUID.randomUUID().toString());
            case "WITH_NO_CONVERSATION" -> get("/api/conversations/with").param("userId", outsiderId.toString());
            case "WITH_SELF" -> get("/api/conversations/with").param("userId", requesterId.toString());
            default -> throw new IllegalArgumentException("지원하지 않는 실패 상황입니다:" + scenario);
        };

        boolean selfConversation = "WITH_SELF".equals(scenario);

        String expectedExceptionName = selfConversation ? SelfConversationNotAllowedException.class.getSimpleName()
                : ConversationNotFoundException.class.getSimpleName();
        ApiErrorCode expectedErrorCode = selfConversation ? ApiErrorCode.SELF_CONVERSATION_NOT_ALLOWED
                : ApiErrorCode.CONVERSATION_NOT_FOUND;

        mockMvc.perform(request.header(HttpHeaders.AUTHORIZATION, BEARER_PREFIX + accessToken))
                .andExpect(status().is(expectedStatus))
                .andExpect(jsonPath("$.exceptionName")
                        .value(expectedExceptionName))
                .andExpect(jsonPath("$.message").value(expectedErrorCode.getMessage()));

        assertConversationCountAndReadStateUnchanged();
    }


    private void insertUser(UUID userId, String name) {
        jdbcTemplate.update("""
              INSERT INTO users ( id, email, nickname, role, enabled, created_at )
              VALUES (?, ?, ?, 'USER', true, CURRENT_TIMESTAMP)
              """, userId, userId + "@test.example", name);
    }

    private void insertConversation(
            UUID conversationId,
            UUID firstUserId,
            UUID secondUserId
    ) {
        UUID userAId = firstUserId;
        UUID userBId = secondUserId;

        if (firstUserId.toString().compareTo(secondUserId.toString()) > 0) {
            userAId = secondUserId;
            userBId = firstUserId;
        }

        jdbcTemplate.update("""
              INSERT INTO conversations ( id, user_a_id, user_b_id, created_at )
              VALUES (?, ?, ?, CURRENT_TIMESTAMP)
              """, conversationId, userAId, userBId);
    }

    private void insertUnreadMessage() {
        jdbcTemplate.update("""
              INSERT INTO direct_messages ( id, conversation_id, sender_id, receiver_id,
                                           content, is_read, created_at )
              VALUES (?, ?, ?, ?, ?, false, CURRENT_TIMESTAMP)
              """, messageId, conversationId, withUserId, requesterId, "안녕하세요.");
    }

    private void insertProfileImage(
            UUID userId,
            UUID imageId,
            String imageKey
    ) {
        jdbcTemplate.update("""
              INSERT INTO file_infos ( id, file_key, original_filename, content_type, size, created_at )
              VALUES (?, ?, 'profile.png', 'image/png', 1024, CURRENT_TIMESTAMP)
              """, imageId, imageKey);

        jdbcTemplate.update("""
              UPDATE users
              SET profile_image_id = ?
              WHERE id = ?
              """, imageId, userId);
    }

    private String issueAccessToken(UUID userId) {
        UUID sessionId = UUID.randomUUID();

        given(userSessionRepository.isCurrent(userId, sessionId))
                .willReturn(true);

        return jwtTokenProvider.createAccessToken(userId, sessionId);
    }


    private void assertConversationCountAndReadStateUnchanged() {
        Long conversationCount = jdbcTemplate.queryForObject("""
                            SELECT COUNT(*)
                            FROM conversations
                            WHERE user_a_id IN (?, ?, ?)
                                OR user_b_id IN (?, ?, ?)
                        """, Long.class,
                requesterId, withUserId, outsiderId,
                requesterId, withUserId, outsiderId);

        assertThat(conversationCount)
                .as("조회 후에도 준비한 대화방 1개만 존재해야 한다.")
                .isEqualTo(1L);

        Boolean read = jdbcTemplate.queryForObject("""
                    SELECT is_read
                    FROM direct_messages
                    WHERE id = ?
                """, Boolean.class, messageId);

        assertThat(read)
                .as("대화방 조회만으로 메시지가 읽음 처리되면 안 된다.")
                .isFalse();
    }
}
