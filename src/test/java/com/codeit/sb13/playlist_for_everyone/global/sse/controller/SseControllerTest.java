package com.codeit.sb13.playlist_for_everyone.global.sse.controller;

import com.codeit.sb13.playlist_for_everyone.domain.auth.jwt.JwtTokenProvider;
import com.codeit.sb13.playlist_for_everyone.domain.auth.security.SecurityUser;
import com.codeit.sb13.playlist_for_everyone.domain.user.entity.Role;
import com.codeit.sb13.playlist_for_everyone.domain.user.service.UserService;
import com.codeit.sb13.playlist_for_everyone.global.config.MDCLoggingInterceptor;
import com.codeit.sb13.playlist_for_everyone.global.config.SecurityConfig;
import com.codeit.sb13.playlist_for_everyone.global.sse.service.SseConnectionService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.UUID;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
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
    @DisplayName("인증된 사용자가 최초연결을 요청하면 200과 SEE 스트림을 반환한다.")
    void connectStartsStreamForAuthenticatedUser() throws Exception {
        UUID userId = UUID.randomUUID();
        SecurityUser principal = new SecurityUser(
                userId,
                "sse-test@example.com",
                "",
                Role.USER,
                false
        );

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
                    .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_EVENT_STREAM));

            verify(sseConnectionService).connect(userId);
        }finally {

            emitter.complete();
        }
    }

}