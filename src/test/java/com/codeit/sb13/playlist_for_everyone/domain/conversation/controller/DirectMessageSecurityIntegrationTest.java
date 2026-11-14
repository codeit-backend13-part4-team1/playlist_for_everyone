package com.codeit.sb13.playlist_for_everyone.domain.conversation.controller;

import com.codeit.sb13.playlist_for_everyone.domain.auth.jwt.JwtTokenProvider;
import com.codeit.sb13.playlist_for_everyone.domain.auth.repository.UserSessionRepository;
import com.codeit.sb13.playlist_for_everyone.global.exception.ApiErrorCode;
import com.codeit.sb13.playlist_for_everyone.global.exception.conversation.ConversationNotFoundException;
import com.codeit.sb13.playlist_for_everyone.global.file.storage.FileStorage;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
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
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

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
@DisplayName("DM 메시지 조회·읽음 처리 JWT 인증·CSRF 통합 테스트")
public class DirectMessageSecurityIntegrationTest {

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
    private final UUID otherConversationId = UUID.randomUUID();

    private final UUID receivedMessageId = UUID.randomUUID();
    private final UUID sentMessageId = UUID.randomUUID();
    private final UUID otherConversationMessageId = UUID.randomUUID();

    private final LocalDateTime baseCreatedAt = LocalDateTime.parse("2026-11-14T10:00:00.123456");

    private final UUID requesterProfileImageId = UUID.randomUUID();
    private final UUID withUserProfileImageId = UUID.randomUUID();
    private final String requesterProfileImageKey = "profiles/" + requesterProfileImageId + ".png";
    private final String withUserProfileImageKey = "profiles/" + withUserProfileImageId + ".png";

    public final String BEARER_PREFIX = "Bearer ";

    @BeforeEach
    void setUp() {
        insertUser(requesterId, "요청자");
        insertUser(withUserId, "대화 상대");
        insertUser(outsiderId, "비참여자");

        insertConversation(conversationId, requesterId, withUserId);
        insertConversation(otherConversationId, requesterId, outsiderId);

        insertUnreadMessage(
                receivedMessageId, conversationId, withUserId, requesterId,
                "수신 메시지", baseCreatedAt
        );

        insertUnreadMessage(
                sentMessageId, conversationId, requesterId, withUserId,
                "발신 메시지",
                baseCreatedAt.plusMinutes(1)
        );

        insertUnreadMessage(
                otherConversationMessageId, otherConversationId, outsiderId, requesterId,
                "다른 대화방 메시지",
                baseCreatedAt.plusMinutes(2)
        );

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
    }

    @AfterEach
    void cleanUp() {
        jdbcTemplate.update("""
          DELETE FROM direct_messages
          WHERE conversation_id IN (?, ?)
          """, conversationId, otherConversationId);

        jdbcTemplate.update("""
          DELETE FROM conversations
          WHERE id IN (?, ?)
          """, conversationId, otherConversationId);

        jdbcTemplate.update("""
              DELETE FROM users
              WHERE id IN (?, ?, ?)
              """, requesterId, withUserId, outsiderId);

        jdbcTemplate.update("""
          DELETE FROM file_infos
          WHERE id IN (?, ?)
          """, requesterProfileImageId, withUserProfileImageId);
    }

    @ParameterizedTest(name = "요청자 계정으로 조회={0}")
    @ValueSource(booleans = {true, false})
    @DisplayName("두 참여자는 JWT만으로 메시지를 조회하고 DB 상태를 변경하지 않는다.")
    void search_returnsMessagesForParticipantsWithoutCsrfAndPreservesState(boolean asRequester) throws Exception {
        UUID authenticatedUserId = asRequester ? requesterId : withUserId;
        String accessToken = issueAccessToken(authenticatedUserId);
        String imageUrlPrefix = "https://example.com/";

        List<Map<String, Object>> before = findMessages();

        given(fileStorage.getFileUrl(anyString()))
                .willAnswer(invocation ->{
                    assertThat(TransactionSynchronizationManager.isActualTransactionActive())
                            .as("프로필 이미지 URL은 조회 트랜잭션 밖에서 생성해야 한다.")
                            .isFalse();

                    String  imageKey = invocation.getArgument(0);
                    return imageUrlPrefix + imageKey;
                });

        mockMvc.perform(get("/api/conversations/{conversationId}/direct-messages", conversationId)
                .header(HttpHeaders.AUTHORIZATION, BEARER_PREFIX+ accessToken)
                .accept(MediaType.APPLICATION_JSON)
                .param("limit","10")
                .param("sortBy","createdAt")
                .param("sortDirection","ASCENDING"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.data[*].id")
                        .value(contains(receivedMessageId.toString(), sentMessageId.toString())))
                .andExpect(jsonPath("$.totalCount").value(2))
                .andExpect(jsonPath("$.data[0].sender.profileImageUrl").value(imageUrlPrefix + withUserProfileImageKey))
                .andExpect(jsonPath("$.data[0].receiver.profileImageUrl").value(imageUrlPrefix + requesterProfileImageKey))
                .andExpect(jsonPath("$.data[1].sender.profileImageUrl").value(imageUrlPrefix + requesterProfileImageKey))
                .andExpect(jsonPath("$.data[1].receiver.profileImageUrl").value(imageUrlPrefix + withUserProfileImageKey));

        assertThat(findMessages())
                .as("조회 후에도 두 대화방의 메시지 데이터가 유지되어야 한다.")
                .isEqualTo(before);
    }

    @Test
    @DisplayName("유효한 JWT와 정상 CSRF로 요청하면 수신 메시지를 읽음 처리하고 200 빈 응답을 반환한다.")
    void read_returnsOkWithValidJwtAndCsrfAndUpdatesReceivedMessage() throws Exception {
        String accessToken = issueAccessToken(requesterId);
        Cookie csrfCookie = issueCsrfCookie();

        List<Map<String, Object>> before = findMessages();

        mockMvc.perform(post("/api/conversations/{conversationId}/direct-messages/{directMessageId}/read",
                conversationId,receivedMessageId)
                .header(HttpHeaders.AUTHORIZATION, BEARER_PREFIX + accessToken)
                .cookie(csrfCookie)
                .header("X-XSRF-TOKEN", csrfCookie.getValue()))
                .andExpect(status().isOk())
                .andExpect(content().string(""));

        List<Map<String, Object>> after = findMessages();

        assertThat(after)
                .filteredOn(row -> receivedMessageId.equals(row.get("id")))
                .singleElement()
                .satisfies(row -> {
                    assertThat(row).containsEntry("is_read", true);
                    assertThat(row.get("updated_at")).isNotNull();
                });

        assertThat(after)
                .filteredOn(row -> !receivedMessageId.equals(row.get("id")))
                .as("발신 메시지와 다른 대화방 메시지는 변경되지 않아야 한다.")
                .containsExactlyElementsOf(
                        before.stream()
                                .filter(row -> !receivedMessageId.equals(row.get("id")))
                                .toList()
                );
    }

    @ParameterizedTest(name = "요청 유형={0}")
    @ValueSource(strings = {"SEARCH", "READ"})
    @DisplayName("JWT가 없으면 메시지 조회·읽음 요청에 401을 반환하고 DB 상태를 유지한다.")
    void requests_returnUnauthorizedWhenJwtMissing(String requestType) throws Exception {
        List<Map<String, Object>> before = findMessages();

        MockHttpServletRequestBuilder request = switch (requestType){
            case "SEARCH" -> get("/api/conversations/{conversationId}/direct-messages",
                    conversationId)
                    .param("limit","10")
                    .param("sortBy","createdAt")
                    .param("sortDirection","ASCENDING");
            case "READ" -> {
                Cookie csrfCookie = issueCsrfCookie();
                yield post("/api/conversations/{conversationId}/direct-messages/{directMessageId}/read",
                        conversationId, receivedMessageId)
                        .cookie(csrfCookie)
                        .header("X-XSRF-TOKEN", csrfCookie.getValue());
            }
            default -> throw new IllegalArgumentException("지원하지 않는 요청 유형입니다:" + requestType);
        };

        mockMvc.perform(request.accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.exceptionName").value("UnauthorizedException"))
                .andExpect(jsonPath("$.message").value("인증이 필요합니다."))
                .andExpect(jsonPath("$.details").isMap())
                .andExpect(jsonPath("$.details").isEmpty());

        assertThat(findMessages())
                .as("인증되지 않은 요청으로 메시지데이터가 변경되면 안 된다.")
                .isEqualTo(before);
    }

    @ParameterizedTest(name = "CSRF 오류 유형={0}")
    @ValueSource(strings = {"MISSING", "MISMATCH"})
    @DisplayName("유효한 JWT를 전달해도 CSRF가 없거나 일치하지 않으면 403을 반환하고 DB 상태를 유지한다.")
    void read_returnsForbiddenWhenCsrfMissingOrMismatched(String scenario) throws Exception {
        String accessToken = issueAccessToken(requesterId);

        List<Map<String, Object>> before = findMessages();
        MockHttpServletRequestBuilder request = post("/api/conversations/{conversationId}/direct-messages/{directMessageId}/read",
                conversationId, receivedMessageId)
                .header(HttpHeaders.AUTHORIZATION, BEARER_PREFIX + accessToken);

        if ("MISMATCH".equals(scenario)){
            Cookie csrfCookie = issueCsrfCookie();
            request.cookie(csrfCookie)
                    .header("X-XSRF-TOKEN", csrfCookie.getValue() + "-changed");
        }

        mockMvc.perform(request.accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.exceptionName")
                        .value(AccessDeniedException.class.getSimpleName()))
                .andExpect(jsonPath("$.message").value("접근 권한이 없습니다."))
                .andExpect(jsonPath("$.details").isMap())
                .andExpect(jsonPath("$.details").isEmpty());

        assertThat(findMessages())
                .as("CSRF 검증에 실패한 요청으로 메시지 데이터가 변경되면 안된다.")
                .isEqualTo(before);
    }

    @ParameterizedTest(name = "요청 유형={0}, 실패 상황={1}")
    @CsvSource({
            "SEARCH, MISSING",
            "SEARCH, NOT_PARTICIPANT",
            "READ, MISSING",
            "READ, NOT_PARTICIPANT"
    })
    @DisplayName("대화방이 없거나 비참여자가 요청하면 404를 반환하고 DB 상태를 유지한다.")
    void requests_returnNotFoundWhenConversationUnavailable(String requestType, String scenario) throws Exception {
        UUID authenticatedUserId = "NOT_PARTICIPANT".equals(scenario) ? outsiderId : requesterId;
        UUID requestedConversationId = "MISSING".equals(scenario) ? UUID.randomUUID() : conversationId;

        String accessToken = issueAccessToken(authenticatedUserId);
        List<Map<String, Object>> before = findMessages();

        MockHttpServletRequestBuilder request = switch (requestType){
            case "SEARCH" -> get("/api/conversations/{conversationId}/direct-messages",
                    requestedConversationId)
                    .param("limit","10")
                    .param("sortBy","createdAt")
                    .param("sortDirection","ASCENDING");

            case "READ" -> {
                Cookie csrfCookie = issueCsrfCookie();
                yield post("/api/conversations/{conversationId}/direct-messages/{directMessageId}/read",
                        requestedConversationId, receivedMessageId)
                        .cookie(csrfCookie)
                        .header("X-XSRF-TOKEN", csrfCookie.getValue());
            }
            default -> throw new IllegalArgumentException("지원하지 않는 요청 유형입니다:" + requestType);
        };

        mockMvc.perform(request.header(HttpHeaders.AUTHORIZATION, BEARER_PREFIX + accessToken)
                .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.exceptionName").value(ConversationNotFoundException.class.getSimpleName()))
                .andExpect(jsonPath("$.message").value(ApiErrorCode.CONVERSATION_NOT_FOUND.getMessage()))
                .andExpect(jsonPath("$.details").isMap());

        assertThat(findMessages())
                .as("대화방 미존재·비참여 요청으로 메시지 데이터가 변경되면 안 된다.")
                .isEqualTo(before);
    }

    @ParameterizedTest(name = "기준 메시지 오류 유형={0}")
    @ValueSource(strings = {"MISSING", "OTHER_CONVERSATION"})
    @DisplayName("기준 메시지가 없거나 다른 대화방에 속하면 404를 반환하고 DB 상태를 유지한다.")
    void read_returnsNotFoundWhenReferenceMessageUnavailable(String scenario) throws Exception {
        UUID referenceMessageId = "MISSING".equals(scenario) ? UUID.randomUUID() : otherConversationMessageId;
        String accessToken = issueAccessToken(requesterId);
        Cookie csrfCookie = issueCsrfCookie();
        List<Map<String, Object>> before = findMessages();

        mockMvc.perform(post("/api/conversations/{conversationId}/direct-messages/{directMessageId}/read",
                conversationId,referenceMessageId)
                .header(HttpHeaders.AUTHORIZATION, BEARER_PREFIX + accessToken)
                .cookie(csrfCookie)
                .header("X-XSRF-TOKEN", csrfCookie.getValue())
                .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.exceptionName").isString())
                .andExpect(jsonPath("$.message").isString())
                .andExpect(jsonPath("$.details").isMap());

        assertThat(findMessages())
                .as("유효하지 않은 기준 메시지로 읽음 상태가 변경되면 안 된다.")
                .isEqualTo(before);
    }


    private void insertUser(UUID userId, String name) {
        jdbcTemplate.update("""
              INSERT INTO users (
                  id,
                  email,
                  nickname,
                  role,
                  enabled,
                  created_at
              )
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
              INSERT INTO conversations (
                  id,
                  user_a_id,
                  user_b_id,
                  created_at
              )
              VALUES (?, ?, ?, CURRENT_TIMESTAMP)
              """, conversationId, userAId, userBId);
    }

    private void insertUnreadMessage(
            UUID messageId,
            UUID conversationId,
            UUID senderId,
            UUID receiverId,
            String content,
            LocalDateTime createdAt
    ) {
        jdbcTemplate.update("""
              INSERT INTO direct_messages (
                  id,
                  conversation_id,
                  sender_id,
                  receiver_id,
                  content,
                  is_read,
                  created_at,
                  updated_at
              )
              VALUES (?, ?, ?, ?, ?, false, ?,
              NULL)
              """, messageId, conversationId, senderId, receiverId, content, createdAt);
    }

    private void insertProfileImage(
            UUID userId,
            UUID imageId,
            String imageKey
    ) {
        jdbcTemplate.update("""
              INSERT INTO file_infos (
                  id,
                  file_key,
                  original_filename,
                  content_type,
                  size,
                  created_at
              )
              VALUES (?, ?, 'profile.png', 'image/png', 1024,CURRENT_TIMESTAMP)
              """, imageId, imageKey);

        jdbcTemplate.update("""
              UPDATE users
              SET profile_image_id = ?
              WHERE id = ?
              """, imageId, userId);
    }

    private String issueAccessToken(UUID userId) {
        UUID sessionId = UUID.randomUUID();

        given(userSessionRepository.isCurrent(userId, sessionId)).willReturn(true);
        return jwtTokenProvider.createAccessToken(userId, sessionId);
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

    private List<Map<String, Object>> findMessages() {
        return jdbcTemplate.queryForList("""
              SELECT id, conversation_id, sender_id, receiver_id, content, is_read, created_at, updated_at
              FROM direct_messages
              WHERE conversation_id IN (?, ?)
              ORDER BY id
              """, conversationId, otherConversationId);
    }

}
