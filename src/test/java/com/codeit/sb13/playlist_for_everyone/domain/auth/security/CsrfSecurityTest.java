package com.codeit.sb13.playlist_for_everyone.domain.auth.security;

import com.codeit.sb13.playlist_for_everyone.domain.auth.controller.AuthController;
import com.codeit.sb13.playlist_for_everyone.domain.auth.jwt.JwtTokenProvider;
import com.codeit.sb13.playlist_for_everyone.domain.auth.repository.RefreshTokenRepository;
import com.codeit.sb13.playlist_for_everyone.domain.auth.service.AuthService;
import com.codeit.sb13.playlist_for_everyone.domain.user.service.UserService;
import com.codeit.sb13.playlist_for_everyone.global.config.MDCLoggingInterceptor;
import com.codeit.sb13.playlist_for_everyone.global.config.SecurityConfig;
import com.codeit.sb13.playlist_for_everyone.global.controller.GlobalExceptionHandler;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.security.authentication.AuthenticationManager;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

@WebMvcTest(controllers = AuthController.class)
@AutoConfigureMockMvc(addFilters = true)
@ActiveProfiles("test")
@Import({
        SecurityConfig.class,
        MDCLoggingInterceptor.class,
        GlobalExceptionHandler.class
})
@DisplayName("CSRF 토큰 발급 API 보안 연결")
class CsrfSecurityTest {

    private static final String CSRF_TOKEN_URL = "/api/auth/csrf-token";
    private static final String CSRF_COOKIE_NAME = "XSRF-TOKEN";

    @Autowired
    private MockMvc mockMvc;

    // 실제 SecurityFilterChain은 사용한다.
    // 아래 객체들은 보안 설정을 생성하는 데 필요한 의존성이다.
    @MockitoBean
    private AuthenticationManager  authenticationManager;

    @MockitoBean
    private JwtTokenProvider jwtTokenProvider;

    @MockitoBean
    private UserService  userService;

    @MockitoBean
    private RefreshTokenRepository refreshTokenRepository;

    @MockitoBean
    private AuthService authService;

    @Test
    @DisplayName("로그인하지 않아도 CSRF 토큰 발급 API를 호출할 수 있다.")
    void 비로그인_사용자도_CSRF_토큰을_발급받는다() throws Exception {
        // 로그인 Principal, Authorization 헤더, 기존 쿠키 없이 호출한다.
        // CSRF 토큰은 로그인 요청을 보내기 전에도 받을 수 있어야 한다.
        mockMvc.perform(get(CSRF_TOKEN_URL))
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));

        // CSRF 토큰 발급에는 사용자 조회나 JWT 발급이 필요하지 않다.
        verifyNoInteractions(
                authenticationManager,
                jwtTokenProvider,
                userService,
                refreshTokenRepository,
                authService
        );

    }


    @Test
    @DisplayName("프론트에서 읽을 수 있는 XSRF-TOKEN 쿠키를 발급한다.")
    void CSRF_토큰을_읽을_수_있는_쿠키로_전달한다() throws Exception {
        MvcResult result = mockMvc.perform(get(CSRF_TOKEN_URL))
                .andExpect(status().isNoContent())
                .andReturn();

        Cookie csrfCookie = result.getResponse()
                .getCookie(CSRF_COOKIE_NAME);

        assertThat(csrfCookie).isNotNull();
        assertThat(csrfCookie.getValue()).isNotBlank();

        // 프론트가 쿠키를 읽어 X-XSRF-TOKEN 헤더에 넣어야 한다.
        // 따라서 CSRF 쿠키는 JavaScript에서 읽을 수 있어야 한다.
        assertThat(csrfCookie.isHttpOnly()).isFalse();

        // 프론트 페이지와 다른 API 경로에서도 사용할 수 있도록 한다.
        assertThat(csrfCookie.getPath()).isEqualTo("/");
    }

    @Test
    @DisplayName("CSRF 토큰 발급 시 HTTP 세션과 Refresh Token을 만들지 않는다")
    void 인증_세션과_Refresh_Token을_생성하지_않는다() throws Exception {
        MvcResult result = mockMvc.perform(get(CSRF_TOKEN_URL))
                .andExpect(status().isNoContent())
                .andReturn();

        // CSRF 토큰을 서버의 HTTP 세션에 저장하지 않는다.
        assertThat(result.getRequest().getSession(false)).isNull();
        assertThat(result.getResponse().getCookie("JSESSIONID"))
                .isNull();

        // CSRF 토큰 발급은 로그인이나 로그인 유지 토큰 발급이 아니다.
        assertThat(result.getResponse().getCookie("REFRESH_TOKEN"))
                .isNull();

        verifyNoInteractions(refreshTokenRepository, authService);
    }




}
