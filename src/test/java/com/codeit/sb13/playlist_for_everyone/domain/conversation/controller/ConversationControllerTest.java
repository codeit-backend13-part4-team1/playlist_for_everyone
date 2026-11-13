package com.codeit.sb13.playlist_for_everyone.domain.conversation.controller;

import com.codeit.sb13.playlist_for_everyone.domain.auth.security.SecurityUser;
import com.codeit.sb13.playlist_for_everyone.domain.conversation.dto.command.ConversationCreateCommand;
import com.codeit.sb13.playlist_for_everyone.domain.conversation.dto.response.ConversationDto;
import com.codeit.sb13.playlist_for_everyone.domain.conversation.dto.response.MessageDto;
import com.codeit.sb13.playlist_for_everyone.domain.conversation.service.ConversationService;
import com.codeit.sb13.playlist_for_everyone.domain.user.dto.response.UserSummary;
import com.codeit.sb13.playlist_for_everyone.domain.user.entity.Role;
import com.codeit.sb13.playlist_for_everyone.global.config.MDCLoggingInterceptor;
import com.codeit.sb13.playlist_for_everyone.global.config.WebMvcConfig;
import com.codeit.sb13.playlist_for_everyone.global.controller.GlobalExceptionHandler;
import com.codeit.sb13.playlist_for_everyone.global.exception.ApiErrorCode;
import com.codeit.sb13.playlist_for_everyone.global.exception.conversation.ConversationCreationFailedException;
import com.codeit.sb13.playlist_for_everyone.global.exception.conversation.ConversationTargetNotFoundException;
import com.codeit.sb13.playlist_for_everyone.global.exception.conversation.SelfConversationNotAllowedException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.MethodArgumentNotValidException;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

import static org.hamcrest.Matchers.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;


@WebMvcTest(ConversationController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import({
        WebMvcConfig.class,
        MDCLoggingInterceptor.class,
        GlobalExceptionHandler.class
})
@DisplayName("대화방 생성 컨트롤러")
class ConversationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ConversationService conversationService;

    private final UUID requesterId = UUID.randomUUID();
    private final UUID withUserId = UUID.randomUUID();
    private final UUID conversationId = UUID.randomUUID();

    @AfterEach
    void clearSecurityContext(){
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("인증 사용자 ID로 신규 대화방 생성을 요청하고 200과 초기 응답을 반환한다.")
    void create_returnsOkWithNewConversation() throws Exception {

        String profileImageUrl = "https://example.com/profile.png";

        setUpUserAuthentication(requesterId);

        ConversationCreateCommand command = new ConversationCreateCommand(requesterId, withUserId);

        ConversationDto response = new ConversationDto(conversationId,
                new UserSummary(withUserId, "대화 상대", profileImageUrl),
                null,
                false);

        given(conversationService.create(command)).willReturn(response);
        mockMvc.perform(post("/api/conversations")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {
                            "withUserId": "%s"
                        }
                        """.formatted(withUserId)))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.id").value(conversationId.toString()))
                .andExpect(jsonPath("$.with.userId").value(withUserId.toString()))
                .andExpect(jsonPath("$.with.name").value("대화 상대"))
                .andExpect(jsonPath("$.with.profileImageUrl").value(profileImageUrl))
                .andExpect(jsonPath("$.lastestMessage").value(nullValue()))
                .andExpect(jsonPath("$.hasUnread").value(false));

        verify(conversationService).create(command);
    }

    @Test
    @DisplayName("기존 대화방의 ID와 최신 메시지 및 미읽음 상태를 유지하여 200으로 반환한다.")
    void create_returnsOkWithExistingConversation() throws Exception {
        UUID messageId = UUID.randomUUID();
        LocalDateTime createdAt = LocalDateTime.of(2026, 11, 12, 10, 30, 15);

        setUpUserAuthentication(requesterId);

        UserSummary requester = new UserSummary(requesterId, "요청자", null);
        UserSummary withUser = new UserSummary(withUserId, "대화 상대", null);

        MessageDto latestMessage = new MessageDto(
                messageId,
                conversationId,
                createdAt,
                withUser,
                requester,
                "안녕하세요."
        );

        ConversationDto response = new ConversationDto(conversationId, withUser, latestMessage, true);

        ConversationCreateCommand command = new ConversationCreateCommand(requesterId, withUserId);
        given(conversationService.create(command)).willReturn(response);

        mockMvc.perform(post("/api/conversations")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {
                            "withUserId": "%s"
                        }
                        """.formatted(withUserId)))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.id").value(conversationId.toString()))
                .andExpect(jsonPath("$.with.userId").value(withUserId.toString()))
                .andExpect(jsonPath("$.with.name").value("대화 상대"))
                .andExpect(jsonPath("$.with.profileImageUrl").value(nullValue()))
                .andExpect(jsonPath("$.lastestMessage.id").value(messageId.toString()))
                .andExpect(jsonPath("$.lastestMessage.conversationId").value(conversationId.toString()))
                .andExpect(jsonPath("$.lastestMessage.createdAt").value(createdAt.toString()))
                .andExpect(jsonPath("$.lastestMessage.sender.userId").value(withUserId.toString()))
                .andExpect(jsonPath("$.lastestMessage.receiver.userId").value(requesterId.toString()))
                .andExpect(jsonPath("$.lastestMessage.content").value("안녕하세요."))
                .andExpect(jsonPath("$.hasUnread").value(true));

        verify(conversationService).create(command);


    }

    @Test
    @DisplayName("대화 상대 ID가 누락되면 400과 검증 오류를 반환하고 서비스를 호출하지 않는다.")
    void create_returnsBadRequestWhenWithUserIdMissing() throws Exception {
        setUpUserAuthentication(requesterId);

        mockMvc.perform(post("/api/conversations")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.exceptionName").value(MethodArgumentNotValidException.class.getSimpleName()))
                .andExpect(jsonPath("$.message").value(ApiErrorCode.INVALID_REQUEST.getMessage()))
                .andExpect(jsonPath("$.details.withUserId").isArray())
                .andExpect(jsonPath("$.details.withUserId").isNotEmpty());
        verifyNoInteractions(conversationService);
    }

    @Test
    @DisplayName("대화 상대 ID가 null이면 400과 검증 오류를 반환하고 서비스를 호출하지 않는다.")
    void create_returnsBadRequestWhenWithUserIdNull() throws Exception {
        setUpUserAuthentication(requesterId);

        mockMvc.perform(post("/api/conversations")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {
                            "withUserId": null
                        }
                        """))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.exceptionName").value(MethodArgumentNotValidException.class.getSimpleName()))
                .andExpect(jsonPath("$.message").value(ApiErrorCode.INVALID_REQUEST.getMessage()))
                .andExpect(jsonPath("$.details.withUserId").isArray())
                .andExpect(jsonPath("$.details.withUserId").isNotEmpty());
        verifyNoInteractions(conversationService);
    }

    @Test
    @DisplayName("대화 상대 ID의 UUID 형식이 잘못되면 400을 반환하고 서비스를 호출하지 않는다.")
    void create_returnsBadRequestWhenWithUserIdInvalid() throws Exception {
        setUpUserAuthentication(requesterId);

        mockMvc.perform(post("/api/conversations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                        {
                            "withUserId": "not-a-uuid"
                        }
                        """))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.exceptionName").value(HttpMessageNotReadableException.class.getSimpleName()))
                .andExpect(jsonPath("$.message").value(ApiErrorCode.INVALID_REQUEST.getMessage()))
                .andExpect(jsonPath("$.details").isMap())
                .andExpect(jsonPath("$.details").isEmpty());
        verifyNoInteractions(conversationService);
    }

    @Test
    @DisplayName("자기 자신과의 대화방 생성이 거절되면 400과 공통 오류 응답을 반환한다.")
    void create_returnsBadRequestWhenSelfConversationRejected() throws Exception {
        setUpUserAuthentication(requesterId);

        ConversationCreateCommand command = new ConversationCreateCommand(requesterId, requesterId);

        given(conversationService.create(command))
                .willThrow(new SelfConversationNotAllowedException());

        mockMvc.perform(post("/api/conversations")
                .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                        {
                            "withUserId": "%s"
                        }
                        """.formatted(requesterId)))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.exceptionName").value(SelfConversationNotAllowedException.class.getSimpleName()))
                .andExpect(jsonPath("$.message").value(ApiErrorCode.SELF_CONVERSATION_NOT_ALLOWED.getMessage()))
                .andExpect(jsonPath("$.details").isMap())
                .andExpect(jsonPath("$.details").isEmpty());

        verify(conversationService).create(command);
    }

    @Test
    @DisplayName("대화 상대가 존재하지 않으면 400과 대상 ID를 포함한 공통 오류 응답을 반환한다.")
    void create_returnsBadRequestWhenWithUserNotFound() throws Exception {
        setUpUserAuthentication(requesterId);

        ConversationCreateCommand command = new ConversationCreateCommand(requesterId, withUserId);

        given(conversationService.create(command))
                .willThrow(new ConversationTargetNotFoundException(Map.of("withUserId", withUserId)));

        mockMvc.perform(post("/api/conversations")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {
                            "withUserId": "%s"
                        }
                        """.formatted(withUserId)))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.exceptionName").value(ConversationTargetNotFoundException.class.getSimpleName()))
                .andExpect(jsonPath("$.message").value(ApiErrorCode.CONVERSATION_TARGET_NOT_FOUND.getMessage()))
                .andExpect(jsonPath("$.details.withUserId").value(withUserId.toString()));

        verify(conversationService).create(command);
    }

    @Test
    @DisplayName("대화방 생성에 실패하면 500과 공통 오류 응답을 반환한다.")
    void create_returnsInternalServerErrorWhenCreationFails() throws Exception {
        setUpUserAuthentication(requesterId);
        ConversationCreateCommand command = new ConversationCreateCommand(requesterId, withUserId);
        ConversationCreationFailedException exception = new ConversationCreationFailedException(new IllegalStateException("대화방 저장 실패"));

        given(conversationService.create(command)).willThrow(exception);

        mockMvc.perform(post("/api/conversations")
                .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                        {
                            "withUserId": "%s"
                        }
                        """.formatted(withUserId)))
                .andExpect(status().isInternalServerError())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.exceptionName").value(ConversationCreationFailedException.class.getSimpleName()))
                .andExpect(jsonPath("$.message").value(ApiErrorCode.CONVERSATION_CREATION_FAILED.getMessage()))
                .andExpect(jsonPath("$.details").isMap())
                .andExpect(jsonPath("$.details").isEmpty());

        verify(conversationService).create(command);
    }

    @Test
    @DisplayName("프로필 이미지 URL 생성 중 예상 밖 예외가 발생하면 내부 메시지를 노출하지 않고 500을 반환한다.")
    void create_returnsInternalServerErrorWhenProfileImageUrlGenerationFails() throws Exception {
        setUpUserAuthentication(requesterId);
        ConversationCreateCommand command = new ConversationCreateCommand(requesterId, withUserId);
        IllegalStateException exception = new IllegalStateException("프로필 이미지 URL 생성 실패");

        given(conversationService.create(command)).willThrow(exception);

        mockMvc.perform(post("/api/conversations")
                .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                        {
                            "withUserId": "%s"
                        }
                        """.formatted(withUserId)))
                .andExpect(status().isInternalServerError())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.exceptionName").value(IllegalStateException.class.getSimpleName()))
                .andExpect(jsonPath("$.message").value(ApiErrorCode.INTERNAL_SERVER_ERROR.getMessage()))
                .andExpect(jsonPath("$.details").isMap())
                .andExpect(jsonPath("$.details").isEmpty())
                .andExpect(content().string(not(containsString(exception.getMessage()))));

        verify(conversationService).create(command);
    }

    private void setUpUserAuthentication(UUID requesterId) {
        SecurityUser principal = new SecurityUser(
                requesterId,
                "requester@example.com",
                "",
                Role.USER,
                false
        );

        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(UsernamePasswordAuthenticationToken.authenticated(
                        principal,
                        null,
                        principal.getAuthorities()));

        SecurityContextHolder.setContext(context);
    }

}
