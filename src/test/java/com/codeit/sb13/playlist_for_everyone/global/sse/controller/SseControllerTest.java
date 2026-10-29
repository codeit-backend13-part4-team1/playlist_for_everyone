package com.codeit.sb13.playlist_for_everyone.global.sse.controller;

import com.codeit.sb13.playlist_for_everyone.domain.auth.jwt.JwtTokenProvider;
import com.codeit.sb13.playlist_for_everyone.domain.auth.security.SecurityUser;
import com.codeit.sb13.playlist_for_everyone.domain.user.entity.Role;
import com.codeit.sb13.playlist_for_everyone.domain.user.entity.User;
import com.codeit.sb13.playlist_for_everyone.domain.user.service.UserService;
import com.codeit.sb13.playlist_for_everyone.global.config.MDCLoggingInterceptor;
import com.codeit.sb13.playlist_for_everyone.global.config.SecurityConfig;
import com.codeit.sb13.playlist_for_everyone.global.exception.ApiErrorCode;
import com.codeit.sb13.playlist_for_everyone.global.sse.service.SseConnectionService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(SseController.class)
@AutoConfigureMockMvc
@Import({
        SecurityConfig.class,
        MDCLoggingInterceptor.class
})
@DisplayName("SSE 연결 컨트롤러")
class SseControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private SseConnectionService sseConnectionService;

    @MockitoBean
    private JwtTokenProvider jwtTokenProvider;

    @MockitoBean
    private UserService userService;

    @MockitoBean
    private AuthenticationManager authenticationManager;

    @Test
    @DisplayName("인증된 사용자가 최초 연결을 요청하면 캐시가 금지된 SEE 스트림을 반환한다.")
    void connectStartsStreamForAuthenticatedUser() throws Exception {
        UUID userId = UUID.randomUUID();
        SecurityUser principal = getPrincipal(userId, "sse-test@example.com");

        SseEmitter emitter = new SseEmitter(30_000L);

        emitter.send(SseEmitter.event().comment("connected"));

        given(sseConnectionService.connect(userId)).willReturn(emitter);

        try {

            mockMvc.perform(get("/api/sse")
                        .with(user(principal))
                        .accept(MediaType.TEXT_EVENT_STREAM)
                    )
                    .andExpect(status().isOk())
                    .andExpect(request().asyncStarted())
                    .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_EVENT_STREAM))
                    .andExpect(header().string(HttpHeaders.CACHE_CONTROL, containsString("no-store")))
            ;

            verify(sseConnectionService).connect(userId);
        }finally {

            emitter.complete();
        }
    }

    @Test
    @DisplayName("미인증 사용자가 SSE 연결을 요청하면 401을 반환하고 연결 서비스를 호출하지 않는다.")
    void connectRejectsUnauthenticatedUser() throws Exception {
        mockMvc.perform(get("/api/sse")
                .accept(MediaType.TEXT_EVENT_STREAM)
        ).andExpect(status().isUnauthorized())
                .andExpect(request().asyncNotStarted());

        verifyNoInteractions(sseConnectionService);
    }

    @Test
    @DisplayName("잘못된 lastEventId로 SSE 연결을 요청하면 400과 공통 JSON 오류 응답을 반환한다.")
    void connectRejectsInvalidLastEventId() throws Exception {
        SecurityUser principal = getPrincipal(UUID.randomUUID(), "see-test@example.com");
        mockMvc.perform(get("/api/sse")
                        .with(user(principal))
                        .param("lastEventId","not-a-uuid")
                        .accept(MediaType.TEXT_EVENT_STREAM)
                )
                .andExpect(status().isBadRequest())
                .andExpect(request().asyncNotStarted())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.exceptionName").value(MethodArgumentTypeMismatchException.class.getSimpleName()))
                .andExpect(jsonPath("$.message").value(ApiErrorCode.INVALID_REQUEST.getMessage()))
                .andExpect(jsonPath("$.details").isMap())
                .andExpect(jsonPath("$.details").isEmpty());

        verifyNoInteractions(sseConnectionService);


    }

    @Test
    @DisplayName("정상적인 lastEventId로 연결을 요청하면 200과 SSE 스트림을 반환한다.")
    void connectAcceptsValidLastEventId() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID lastEventId = UUID.randomUUID();

        SecurityUser principal = getPrincipal(userId, "sse-test@example.com");

        SseEmitter emitter = new SseEmitter(30_000L);

        emitter.send(SseEmitter.event().comment("connected"));

        given(sseConnectionService.connect(userId)).willReturn(emitter);

        try {
            mockMvc.perform(get("/api/sse")
                    .with(user(principal))
                    .param("lastEventId", lastEventId.toString())
                    .accept(MediaType.TEXT_EVENT_STREAM))
                    .andExpect(status().isOk())
                    .andExpect(request().asyncStarted())
                    .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_EVENT_STREAM));

            verify(sseConnectionService).connect(userId);
        } finally {
            emitter.complete();
        }
    }

    @Test
    @DisplayName("요청 파라미터에 다른 사용자 ID가 있어도 인증된 사용자로만 SSE 연결을 새성한다.")
    void connectUsesAuthenticatedUserInsteadOfQueryUserId() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID otherUserId = UUID.randomUUID();

        SecurityUser principal = getPrincipal(userId, "sse-test@example.com");

        SseEmitter emitter = new SseEmitter(30_000L);

        emitter.send(SseEmitter.event().comment("connected"));

        given(sseConnectionService.connect(userId)).willReturn(emitter);

        try{
            mockMvc.perform(get("/api/sse")
                    .with(user(principal))
                    .param("userId", otherUserId.toString())
                    .accept(MediaType.TEXT_EVENT_STREAM))
                    .andExpect(status().isOk())
                    .andExpect(request().asyncStarted())
                    .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_EVENT_STREAM));

            verify(sseConnectionService).connect(userId);
            verify(sseConnectionService, never()).connect(otherUserId);
        } finally {
            emitter.complete();
        }
    }

    @Test
    @DisplayName("인증된 SSE 연결을 종료하면 비동기 재처리도 보안 예외 없이 완료된다.")
    void completeStreamAllowsAsyncDispatchForAuthenticatedUser() throws Exception {
        UUID userId = UUID.randomUUID();
        String token = "sse-test-token";

        User user = mock(User.class);

        given(user.getId()).willReturn(userId);
        given(user.getEmail()).willReturn("sse-test@example.com");
        given(user.getRole()).willReturn(Role.USER);
        given(user.isLocked()).willReturn(false);

        given(jwtTokenProvider.parseAccessToken(token)).willReturn(userId);
        given(userService.getById(userId)).willReturn(user);

        SseEmitter emitter = new SseEmitter(30_000L);

        emitter.send(SseEmitter.event().comment("connected"));
        given(sseConnectionService.connect(userId)).willReturn(emitter);

        try {
            MvcResult result = mockMvc.perform(get("/api/sse")
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                            .accept(MediaType.TEXT_EVENT_STREAM))
                    .andExpect(status().isOk())
                    .andExpect(request().asyncStarted())
                    .andReturn();
            emitter.complete();
            mockMvc.perform(asyncDispatch(result))
                    .andExpect(status().isOk())
                    .andExpect(request().asyncNotStarted());
            verify(sseConnectionService).connect(userId);
        } finally {
            emitter.complete();
        }
    }

    private SecurityUser getPrincipal(UUID userId, String mail) {
        return new SecurityUser(userId, mail, "", Role.USER, false);
    }
}