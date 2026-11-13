package com.codeit.sb13.playlist_for_everyone.domain.auth.security;

import com.codeit.sb13.playlist_for_everyone.domain.auth.controller.AuthController;
import com.codeit.sb13.playlist_for_everyone.domain.auth.jwt.JwtTokenProvider;
import com.codeit.sb13.playlist_for_everyone.domain.auth.repository.RefreshTokenRepository;
import com.codeit.sb13.playlist_for_everyone.domain.auth.repository.UserSessionRepository;
import com.codeit.sb13.playlist_for_everyone.domain.auth.service.AuthService;
import com.codeit.sb13.playlist_for_everyone.domain.auth.service.PasswordResetService;
import com.codeit.sb13.playlist_for_everyone.domain.user.service.UserService;
import com.codeit.sb13.playlist_for_everyone.global.config.MDCLoggingInterceptor;
import com.codeit.sb13.playlist_for_everyone.global.config.SecurityConfig;
import com.codeit.sb13.playlist_for_everyone.global.controller.GlobalExceptionHandler;
import com.codeit.sb13.playlist_for_everyone.global.exception.auth.PasswordResetTooManyRequestsException;
import com.codeit.sb13.playlist_for_everyone.global.exception.user.UserNotFoundException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;

import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = AuthController.class)
@AutoConfigureMockMvc(addFilters = true)
@ActiveProfiles("test")
@Import({
        SecurityConfig.class,
        MDCLoggingInterceptor.class,
        GlobalExceptionHandler.class
})
@DisplayName("비밀번호 초기화 API 보안 연결")
class PasswordResetSecurityTest {

    private static final String URL = "/api/auth/reset-password";
    private static final String EMAIL = "user@gmail.com";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private PasswordResetService passwordResetService;

    // SecurityConfig 생성에 필요한 의존성
    @MockitoBean
    private AuthService authService;

    @MockitoBean
    private AuthenticationManager authenticationManager;

    @MockitoBean
    private JwtTokenProvider jwtTokenProvider;

    @MockitoBean
    private UserService userService;

    @MockitoBean
    private RefreshTokenRepository refreshTokenRepository;

    @MockitoBean
    private UserSessionRepository userSessionRepository;

    @Test
    @DisplayName("로그인하지 않아도 임시 비밀번호를 요청할 수 있다.")
    void 로그인하지_않아도_임시_비밀번호를_요청할_수_있다() throws Exception {
        mockMvc.perform(post(URL)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "email": "%s"
                                }
                                """.formatted(EMAIL)))
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));

        verify(passwordResetService).resetPassword(EMAIL);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "{}",
            "{\"email\": \"\"}",
            "{\"email\": \"   \"}",
            "{\"email\": \"not-an-email\"}"
    })
    @DisplayName("이메일이 없거나 형식이 잘못되면 400을 반환하고 발급하지 않는다.")
    void 이메일이_잘못되면_400을_반환한다(String body) throws Exception {
        mockMvc.perform(post(URL)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(passwordResetService);
    }

    @Test
    @DisplayName("가입하지 않은 이메일이면 404를 반환한다.")
    void 가입하지_않은_이메일이면_404를_반환한다() throws Exception {
        willThrow(new UserNotFoundException(Map.of("email", EMAIL)))
                .given(passwordResetService).resetPassword(EMAIL);

        mockMvc.perform(post(URL)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email": "%s"}
                                """.formatted(EMAIL)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.exceptionName").value("UserNotFoundException"));
    }

    @Test
    @DisplayName("CSRF 토큰이 없으면 거절한다.")
    void CSRF_토큰이_없으면_거절한다() throws Exception {
        mockMvc.perform(post(URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email": "%s"}
                                """.formatted(EMAIL)))
                .andExpect(status().isForbidden());

        verifyNoInteractions(passwordResetService);
    }

    @Test
    @DisplayName("요청 제한을 넘으면 429와 재시도 시간을 반환한다.")
    void 요청_제한을_넘으면_429를_반환한다() throws Exception {
        willThrow(new PasswordResetTooManyRequestsException(45))
                .given(passwordResetService).resetPassword(EMAIL);

        mockMvc.perform(post(URL)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email": "%s"}
                                """.formatted(EMAIL)))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.exceptionName").value("PasswordResetTooManyRequestsException"))
                .andExpect(jsonPath("$.details.retryAfterSeconds").value(45));
    }
}
