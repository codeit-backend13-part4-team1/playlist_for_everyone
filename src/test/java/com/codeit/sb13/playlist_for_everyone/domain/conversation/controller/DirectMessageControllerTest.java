package com.codeit.sb13.playlist_for_everyone.domain.conversation.controller;

import com.codeit.sb13.playlist_for_everyone.domain.auth.security.SecurityUser;
import com.codeit.sb13.playlist_for_everyone.domain.conversation.dto.command.DirectMessageReadCommand;
import com.codeit.sb13.playlist_for_everyone.domain.conversation.dto.repository.DirectMessageSearchCondition;
import com.codeit.sb13.playlist_for_everyone.domain.conversation.dto.response.MessageDto;
import com.codeit.sb13.playlist_for_everyone.domain.conversation.entity.DirectMessageSortBy;
import com.codeit.sb13.playlist_for_everyone.domain.conversation.service.DirectMessageService;
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

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.nullValue;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(DirectMessageController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import({
        WebMvcConfig.class,
        MDCLoggingInterceptor.class,
        GlobalExceptionHandler.class
})
@DisplayName("DM 메시지 조회·읽음 처리 컨트롤러")
class DirectMessageControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private DirectMessageService directMessageService;

    private final UUID requesterId = UUID.randomUUID();
    private final UUID withUserId = UUID.randomUUID();
    private final UUID conversationId = UUID.randomUUID();

    @BeforeEach
    void setUpAuthentication(){
        SecurityUser principal = new SecurityUser(
                requesterId,
                "requester@eexample.com",
                "",
                Role.USER,
                false
        );

        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated(
                        principal,
                        null,
                        principal.getAuthorities()
                )
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
    @DisplayName("메시지 조회 조건과 인증 사용자 ID를 전달하고 커서 응답을 반환한다.")
    void search_passesAuthenticatedRequesterAndReturnsCursorResponse(
            boolean hasCursor, SortDirection direction) throws Exception {

        String cursor = hasCursor ? "2026-11-14T10:00:00.123456" : null;
        UUID idAfter = hasCursor ? UUID.randomUUID() : null;

        DirectMessageSearchCondition condition = new DirectMessageSearchCondition(conversationId,
                cursor,
                idAfter,
                1,
                direction,
                DirectMessageSortBy.CREATED_AT,
                requesterId);

        UUID messageId = UUID.randomUUID();
        LocalDateTime createdAt = LocalDateTime.parse("2026-11-14T09:30:00.654321");

        MessageDto message = new MessageDto(
                messageId,
                conversationId,
                createdAt,
                new UserSummary(withUserId, "대화 상대", null),
                new UserSummary(requesterId, "요청자", null),
                "안녕하세요."
        );

        CursorResponse<MessageDto> response = new CursorResponse<>(
                List.of(message),
                createdAt.toString(),
                messageId,
                true,
                3L,
                "createdAt",
                direction
        );

        given(directMessageService.search(condition)).willReturn(response);
        MockHttpServletRequestBuilder request = get("/api/conversations/{conversationId}/direct-messages",
                conversationId)
                .accept(MediaType.APPLICATION_JSON)
                .param("limit","1")
                .param("sortBy", "createdAt")
                .param("sortDirection", direction.name())
                .param("requesterId", withUserId.toString());

        if (hasCursor){
            request.param("cursor", cursor)
                    .param("idAfter", idAfter.toString());
        }

        mockMvc.perform(request)
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].id").value(messageId.toString()))
                .andExpect(jsonPath("$.data[0].conversationId").value(conversationId.toString()))
                .andExpect(jsonPath("$.data[0].createdAt").value(createdAt.toString()))
                .andExpect(jsonPath("$.data[0].sender.userId").value(withUserId.toString()))
                .andExpect(jsonPath("$.data[0].sender.name").value("대화 상대"))
                .andExpect(jsonPath("$.data[0].sender.profileImageUrl").value(nullValue()))
                .andExpect(jsonPath("$.data[0].receiver.userId").value(requesterId.toString()))
                .andExpect(jsonPath("$.data[0].receiver.name").value("요청자"))
                .andExpect(jsonPath("$.data[0].receiver.profileImageUrl").value(nullValue()))
                .andExpect(jsonPath("$.data[0].content").value("안녕하세요."))
                .andExpect(jsonPath("$.nextCursor").value(createdAt.toString()))
                .andExpect(jsonPath("$.nextIdAfter").value(messageId.toString()))
                .andExpect(jsonPath("$.hasNext").value(true))
                .andExpect(jsonPath("$.totalCount").value(3))
                .andExpect(jsonPath("$.sortBy").value("createdAt"))
                .andExpect(jsonPath("$.sortDirection").value(direction.name()));

        verify(directMessageService).search(condition);

    }

    @Test
    @DisplayName("요청 본문 없이 경로와 인증 사용자ID로 읽음 처리하고 200 빈 응답을 반환한다.")
    void read_passesAuthenticatedRequesterAndReturnsEmptyResponse() throws Exception {
        UUID directMessageId = UUID.randomUUID();
        DirectMessageReadCommand command = new DirectMessageReadCommand(conversationId, directMessageId, requesterId);

        mockMvc.perform(post("/api/conversations/{conversationId}/direct-messages/{directMessageId}/read",
                        conversationId, directMessageId)
                .param("requesterId", withUserId.toString()))
                .andExpect(status().isOk())
                .andExpect(content().string(""));

        verify(directMessageService).read(command);
    }

    @ParameterizedTest(name = "누락된 필수 파라미터={0}")
    @ValueSource(strings = {"limit", "sortBy", "sortDirection"})
    @DisplayName("필수 조회 파라미터가 누락되면 400을 반환하고 서비스를 호출하지 않는다.")
    void search_returnsBadRequestWhenRequiredParameterMissing(String missingParameter) throws Exception {
        LinkedMultiValueMap<String, String > params = new LinkedMultiValueMap<>();
        params.set("limit","1");
        params.set("sortBy","createdAt");
        params.set("sortDirection","DESCENDING");
        params.remove(missingParameter);

        mockMvc.perform(get("/api/conversations/{conversationId}/direct-messages", conversationId)
                .accept(MediaType.APPLICATION_JSON)
                .params(params))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.message").value(ApiErrorCode.INVALID_REQUEST.getMessage()));
        verifyNoInteractions(directMessageService);
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
            "cursor, ''"
    })
    @DisplayName("조회 파라미터 값이 잘못되면 400을 반환하고 서비스를 호출하지 않는다.")
    void search_returnsBadRequestWhenParameterValueInvalid(
            String invalidParameter, String invalidValue
    ) throws Exception {
        LinkedMultiValueMap<String, String > params = new LinkedMultiValueMap<>();
        params.set("limit","1");
        params.set("sortBy","createdAt");
        params.set("sortDirection","DESCENDING");
        params.set("cursor", "2026-11-14T10:00:00.123456");
        params.set("idAfter", "00000000-0000-0000-0000-000000000001");
        params.set(invalidParameter, invalidValue);

        mockMvc.perform(get("/api/conversations/{conversationId}/direct-messages", conversationId)
                .accept(MediaType.APPLICATION_JSON)
                .params(params))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.message").value(ApiErrorCode.INVALID_REQUEST.getMessage()));

        verifyNoInteractions(directMessageService);

    }

    @ParameterizedTest(name = "누락된 커서 파라미터={0}")
    @ValueSource(strings = {"cursor", "idAfter"})
    @DisplayName("커서 두 값 중 하나만 전달하면 400을 반환하고 서비스를 호출하지 않는다.")
    void search_returnsBadRequestWhenOnlyOneCursorParameterProvided(String missingParameter) throws Exception {
        LinkedMultiValueMap<String, String > params = new LinkedMultiValueMap<>();
        params.set("limit","1");
        params.set("sortBy","createdAt");
        params.set("sortDirection","DESCENDING");
        params.set("cursor", "2026-11-14T10:00:00.123456");
        params.set("idAfter", "00000000-0000-0000-0000-000000000001");
        params.remove(missingParameter);

        mockMvc.perform(get("/api/conversations/{conversationId}/direct-messages", conversationId)
                .accept(MediaType.APPLICATION_JSON)
                .params(params))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.message").value(ApiErrorCode.INVALID_REQUEST.getMessage()));

        verifyNoInteractions(directMessageService);
    }

    @ParameterizedTest(name = "요청={0}, 잘못된 경로 변수={1}")
    @CsvSource({
            "GET, conversationId",
            "POST, conversationId",
            "POST, directMessageId"
    })
    @DisplayName("경로의 UUID 형식이 잘못되면 400을 반환하고 서비스를 호출하지 않는다.")
    void request_returnsBadRequestWhenPathUuidInvalid(String method,String  invalidPathVariable) throws Exception {
        String requestedConversationId = "conversationId".equals(invalidPathVariable) ?
                "not-a-uuid" : conversationId.toString();
        String requestedMessageId = "directMessageId".equals(invalidPathVariable) ?
                "not-a-uuid" : UUID.randomUUID().toString();

        MockHttpServletRequestBuilder request;

        if ("GET".equals(method)){
            request = get("/api/conversations/{conversationId}/direct-messages",
                    requestedConversationId)
                    .param("limit", "1")
                    .param("sortBy", "createdAt")
                    .param("sortDirection", "DESCENDING");
        }else {
            request = post("/api/conversations/{conversationId}/direct-messages/{directMessageId}/read",
                    requestedConversationId, requestedMessageId);
        }

        mockMvc.perform(request.accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.message").value(ApiErrorCode.INVALID_REQUEST.getMessage()));

        verifyNoInteractions(directMessageService);

    }


}