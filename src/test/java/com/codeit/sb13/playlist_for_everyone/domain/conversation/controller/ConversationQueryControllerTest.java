package com.codeit.sb13.playlist_for_everyone.domain.conversation.controller;

import com.codeit.sb13.playlist_for_everyone.domain.auth.security.SecurityUser;
import com.codeit.sb13.playlist_for_everyone.domain.conversation.dto.repository.ConversationFindByIdCondition;
import com.codeit.sb13.playlist_for_everyone.domain.conversation.dto.repository.ConversationFindCondition;
import com.codeit.sb13.playlist_for_everyone.domain.conversation.dto.repository.ConversationSearchCondition;
import com.codeit.sb13.playlist_for_everyone.domain.conversation.dto.response.ConversationDto;
import com.codeit.sb13.playlist_for_everyone.domain.conversation.entity.ConversationSortBy;
import com.codeit.sb13.playlist_for_everyone.domain.conversation.service.ConversationService;
import com.codeit.sb13.playlist_for_everyone.domain.user.dto.response.UserSummary;
import com.codeit.sb13.playlist_for_everyone.domain.user.entity.Role;
import com.codeit.sb13.playlist_for_everyone.global.config.MDCLoggingInterceptor;
import com.codeit.sb13.playlist_for_everyone.global.config.WebMvcConfig;
import com.codeit.sb13.playlist_for_everyone.global.controller.GlobalExceptionHandler;
import com.codeit.sb13.playlist_for_everyone.global.domain.SortDirection;
import com.codeit.sb13.playlist_for_everyone.global.dto.response.CursorResponse;
import com.codeit.sb13.playlist_for_everyone.global.exception.ApiErrorCode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.util.LinkedMultiValueMap;

import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.in;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(ConversationController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import({
        WebMvcConfig.class,
        MDCLoggingInterceptor.class,
        GlobalExceptionHandler.class
})
public class ConversationQueryControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ConversationService conversationService;

    private final UUID requesterId = UUID.randomUUID();
    private final UUID withUserId = UUID.randomUUID();
    private final UUID conversationId = UUID.randomUUID();

    @BeforeEach
    void setUpAuthentication(){
        SecurityUser principal = new SecurityUser(requesterId,
                "requester@example.com",
                "",
                Role.USER,
                false);

        SecurityContext context = SecurityContextHolder.createEmptyContext();

        context.setAuthentication(
                UsernamePasswordAuthenticationToken.
                        authenticated(principal, null, principal.getAuthorities())
        );

        SecurityContextHolder.setContext(context);
    }

    @AfterEach
    void clearSecurityContext(){
        SecurityContextHolder.clearContext();
    }

    @ParameterizedTest(name = "커서 사용={0}, 정렬방향={1}")
    @CsvSource({
            "false, ASCENDING",
            "true, DESCENDING"
    })
    @DisplayName("목록 조회 조건과 인증 사용자 ID를 전달하고 커서 응답을 반환한다.")
    void search_passesAuthenticatedRequesterAndReturnsCursorResponse(
            boolean hasCursor,
            SortDirection direction
    ) throws Exception {
        String cursor = hasCursor ? "2026-11-12T10:00:00.123456" : null;
        UUID idAfter = hasCursor ? UUID.randomUUID() : null;

        ConversationSearchCondition condition = new ConversationSearchCondition(
                "Movie",
                cursor,
                idAfter,
                1,
                direction,
                ConversationSortBy.CREATED_AT,
                requesterId
        );

        ConversationDto conversation = new ConversationDto(
                conversationId,
                new UserSummary(withUserId, "대화 상대", null),
                null,
                false
        );

        String nextCursor = "2026-11-12T09:30:00.654321";
        CursorResponse<ConversationDto> response = new CursorResponse<>(
                List.of(conversation),
                nextCursor,
                conversationId,
                true,
                3L,
                "createdAt",
                direction
        );

        given(conversationService.search(condition)).willReturn(response);
        MockHttpServletRequestBuilder request = get("/api/conversations")
                .accept(MediaType.APPLICATION_JSON)
                .param("keywordLike", "Movie")
                .param("limit", "1")
                .param("sortBy", "createdAt")
                .param("sortDirection", direction.name())
                .param("requesterId", UUID.randomUUID().toString());

        if (hasCursor){
            request.param("cursor", cursor)
                    .param("idAfter", idAfter.toString());
        }

        mockMvc.perform(request)
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].id").value(conversationId.toString()))
                .andExpect(jsonPath("$.nextCursor").value(nextCursor))
                .andExpect(jsonPath("$.nextIdAfter").value(conversationId.toString()))
                .andExpect(jsonPath("$.hasNext").value(true))
                .andExpect(jsonPath("$.totalCount").value(3))
                .andExpect(jsonPath("$.sortBy").value("createdAt"))
                .andExpect(jsonPath("$.sortDirection").value(direction.name()));

        verify(conversationService).search(condition);
    }

    @Test
    @DisplayName("인증 사용자 ID와 경로의 대화방 ID를 전달하고 단건 조회 응답을 반환한다.")
    void findById_passesAuthenticatedRequesterAndReturnsConversation() throws Exception {
        ConversationFindByIdCondition condition = new ConversationFindByIdCondition(requesterId, conversationId);

        ConversationDto response = new ConversationDto(
                conversationId,
                new UserSummary(withUserId, "대화 상대", null),
                null,
                false
        );

        given(conversationService.findById(condition)).willReturn(response);

        mockMvc.perform(get("/api/conversations/{conversationId}", conversationId)
                .accept(MediaType.APPLICATION_JSON)
                .param("requesterId", UUID.randomUUID().toString()))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.id").value(conversationId.toString()))
                .andExpect(jsonPath("$.with.userId").value(withUserId.toString()))
                .andExpect(jsonPath("$.lastestMessage").value(nullValue()))
                .andExpect(jsonPath("$.hasUnread").value(false));

        verify(conversationService).findById(condition);
    }

    @Test
    @DisplayName("인증 사용자 ID와 상대방 ID를 전달하고 기존 대화방을 반환한다.")
    void findWithUSer_passesAuthenticatedRequesterAndReturnsConversation() throws Exception {
        ConversationFindCondition condition = new ConversationFindCondition(requesterId, withUserId);

        ConversationDto response = new ConversationDto(
                conversationId,
                new UserSummary(withUserId, "대화 상대", null),
                null,
                false
        );

        given(conversationService.findWithUser(condition)).willReturn(response);

        mockMvc.perform(get("/api/conversations/with")
                .accept(MediaType.APPLICATION_JSON)
                .param("userId", withUserId.toString())
                .param("requesterId",UUID.randomUUID().toString()))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.id").value(conversationId.toString()))
                .andExpect(jsonPath("$.with.userId").value(withUserId.toString()))
                .andExpect(jsonPath("$.lastestMessage").value(nullValue()))
                .andExpect(jsonPath("$.hasUnread").value(false));

        verify(conversationService).findWithUser(condition);
    }

    @ParameterizedTest(name = "경로={0}, 누락 파라미터={1}")
    @CsvSource({
            "/api/conversations, limit",
            "/api/conversations, sortBy",
            "/api/conversations, sortDirection",
            "/api/conversations/with, userId"
    })
    @DisplayName("필수 조회 파라미터가 누락되면 400을 반환하고 서비스를 호출하지 않는다.")
    void query_returnsBadRequestWhenRequiredParameterMissing(String path, String missingParameter) throws Exception {
        LinkedMultiValueMap<String ,String > params = new LinkedMultiValueMap<>();

        if (path.equals("/api/conversations")){
            params.add("limit", "1");
            params.add("sortBy", "createdAt");
            params.add("sortDirection", "DESCENDING");
        } else {
            params.add("userId", withUserId.toString());
        }

        params.remove(missingParameter);

        mockMvc.perform(get(path)
                .accept(MediaType.APPLICATION_JSON)
                .params(params))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.message").value(ApiErrorCode.INVALID_REQUEST.getMessage()));

        verifyNoInteractions(conversationService);
    }

    @ParameterizedTest(name = "잘못된 파라미터={0}, 값={1}")
    @CsvSource({
            "limit, 0",
            "limit, -1",
            "limit, abc",
            "sortBy, updatedAt",
            "sortDirection, SIDEWAYS",
            "idAfter, not-a-uuid",
            "cursor, not-a-date",
            "cursor, 2026-02-30T10:00:00",
            "cursor, ''",
            "userId, not-a-uuid",
            "conversationId, not-a-uuid"
    })
    @DisplayName("조회 파라미터 값이 잘못되면 400을 반환하고 서비스르 호출하지 않는다.")
    void query_returnsBadRequestWhenParameterValueInvalid(String invalidParameter, String invalidValue) throws Exception {
        String path = "/api/conversations";
        LinkedMultiValueMap<String, String> params = new LinkedMultiValueMap<>();

        if (invalidParameter.equals("conversationId")){
            path += "/" + invalidValue;
        } else if (invalidParameter.equals("userId")){
            path += "/with";
            params.set("userId", invalidValue);
        } else {
            params.set("limit", "1");
            params.set("sortBy", "createdAt");
            params.set("sortDirection", "DESCENDING");
            params.set("cursor", "2026-11-12T10:00:00.123456");
            params.set("idAfter", "00000000-0000-0000-0000-000000000001");
            params.set(invalidParameter, invalidValue);
        }

        mockMvc.perform(get(path)
                .accept(MediaType.APPLICATION_JSON)
                .params(params))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.message").value(ApiErrorCode.INVALID_REQUEST.getMessage()));

        verifyNoInteractions(conversationService);
    }

    @ParameterizedTest(name = "누락된 커서 파라미터={0}")
    @ValueSource(strings = {"cursor", "idAfter"})
    @DisplayName("커서 두 값 중 하나만 전달 하면 400을 반환하고 서비스를 호추랗지 않는다.")
    void search_returnsBadRequestWhenOnlyOneCursorParameterProvided(String missingParameter) throws Exception {
        LinkedMultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.set("limit", "1");
        params.set("sortBy", "createdAt");
        params.set("sortDirection", "DESCENDING");
        params.set("cursor", "2026-11-12T10:00:00.123456");
        params.set("idAfter", conversationId.toString());
        params.remove(missingParameter);

        mockMvc.perform(get("/api/conversations")
                .accept(MediaType.APPLICATION_JSON)
                .params(params))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.message").value(ApiErrorCode.INVALID_REQUEST.getMessage()));

        verifyNoInteractions(conversationService);
    }

}
