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
import io.jsonwebtoken.ExpiredJwtException;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(
        controllers = AuthController.class,
        properties = {
                "everyone.jwt.refresh-cookie-secure=true"
        }
)
@AutoConfigureMockMvc(addFilters = true)
@ActiveProfiles("test")
@Import({
        SecurityConfig.class,
        MDCLoggingInterceptor.class,
        GlobalExceptionHandler.class
})
@DisplayName("로그아웃 API 보안 연결")
class LogoutSecurityTest {

    private static final String LOGOUT_URL = "/api/auth/sign-out";
    private static final String REFRESH_TOKEN = "test-refresh-token";
    private static final String EXPIRED_ACCESS_TOKEN = "expired-access-token";

    @Autowired
    private MockMvc mockMvc;

    // 서비스 동작은 기존 단위 테스트에서 검증한다.
    // 여기서는 실제 필터와 핸들러의 연결을 확인한다.
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

    // SecurityConfig가 로그인 세션 저장소를 주입받는다.
    @MockitoBean
    private UserSessionRepository userSessionRepository;

    @MockitoBean
    private PasswordResetService passwordResetService;

    @Test
    @DisplayName("Access Token 없이 Refresh Token 쿠키로 로그아웃한다")
    void 쿠키로_로그아웃한다() throws Exception {
        MvcResult result = mockMvc.perform(
                        logoutRequest()
                                .cookie(refreshCookie(REFRESH_TOKEN))
                )
                .andExpect(status().isNoContent())
                .andExpect(content().string(""))
                .andReturn();

        verify(authService).logout(REFRESH_TOKEN);

        assertRefreshCookieDeleted(result);
        assertNoSessionOrRedirect(result);
    }

    @Test
    @DisplayName("만료된 Access Token이 있어도 로그아웃할 수 있다")
    void 만료된_Access_Token이_로그아웃을_막지_않는다()
            throws Exception {
        // Access Token을 검사하면 만료 예외가 발생하는 상황이다.
        given(jwtTokenProvider.parseAccess(EXPIRED_ACCESS_TOKEN))
                .willThrow(new ExpiredJwtException(
                        null,
                        null,
                        "Access Token이 만료되었습니다."
                ));

        MvcResult result = mockMvc.perform(
                        logoutRequest()
                                .cookie(refreshCookie(REFRESH_TOKEN))
                                .header(
                                        HttpHeaders.AUTHORIZATION,
                                        "Bearer " + EXPIRED_ACCESS_TOKEN
                                )
                )
                .andExpect(status().isNoContent())
                .andExpect(content().string(""))
                .andReturn();

        verify(authService).logout(REFRESH_TOKEN);

        // Access Token 만료 때문에 로그아웃이 차단되면 안 된다.
        verify(jwtTokenProvider, never()).parseAccess(anyString());

        assertRefreshCookieDeleted(result);
    }

    @Test
    @DisplayName("Refresh Token 쿠키가 없어도 204를 반환한다")
    void 쿠키가_없어도_로그아웃한다() throws Exception {
        MvcResult result = mockMvc.perform(logoutRequest())
                .andExpect(status().isNoContent())
                .andExpect(content().string(""))
                .andReturn();

        // 삭제할 토큰이 없으므로 서비스를 호출하지 않는다.
        verifyNoInteractions(authService);

        assertRefreshCookieDeleted(result);
    }

    @Test
    @DisplayName("빈 Refresh Token 쿠키도 정리하고 204를 반환한다")
    void 빈_쿠키도_정리한다() throws Exception {
        MvcResult result = mockMvc.perform(
                        logoutRequest().cookie(refreshCookie(""))
                )
                .andExpect(status().isNoContent())
                .andExpect(content().string(""))
                .andReturn();

        verifyNoInteractions(authService);

        assertRefreshCookieDeleted(result);
    }

    @Test
    @DisplayName("Redis 삭제 실패 시 500 JSON을 반환하고 쿠키를 유지한다")
    void 저장소_장애를_500으로_반환한다() throws Exception {
        doThrow(new DataAccessResourceFailureException("Redis 연결 실패"))
                .when(authService)
                .logout(REFRESH_TOKEN);

        MvcResult result = mockMvc.perform(
                        logoutRequest()
                                .cookie(refreshCookie(REFRESH_TOKEN))
                )
                .andExpect(status().isInternalServerError())
                .andExpect(content().contentTypeCompatibleWith(
                        MediaType.APPLICATION_JSON
                ))
                .andExpect(jsonPath("$.exceptionName")
                        .value("LogoutFailedException"))
                .andExpect(jsonPath("$.message")
                        .value("로그아웃 처리에 실패했습니다. 잠시 후 다시 시도해 주세요."))
                .andReturn();

        // 앞단 필터에서 막힌 결과를 서비스 오류 처리로 착각하지 않도록 검증한다.
        verify(authService).logout(REFRESH_TOKEN);

        // 삭제 실패 시 쿠키를 제거하지 않아 재시도가 가능하도록 한다.
        assertThat(result.getResponse().getHeaders(HttpHeaders.SET_COOKIE))
                .noneMatch(value -> value.startsWith("REFRESH_TOKEN="));

        assertThat(result.getResponse().getContentAsString())
                .doesNotContain(REFRESH_TOKEN, "Redis 연결 실패");

        assertNoSessionOrRedirect(result);
    }

    @Test
    @DisplayName("인증 없는 GET 요청으로는 로그아웃을 실행하지 않는다")
    void GET_요청으로_로그아웃하지_않는다() throws Exception {
        MvcResult result = mockMvc.perform(
                        get(LOGOUT_URL)
                                .cookie(refreshCookie(REFRESH_TOKEN))
                )
                .andExpect(status().isUnauthorized())
                .andReturn();

        // POST 로그아웃만 허용하며 GET 요청은 기존 인증 정책을 따른다.
        verifyNoInteractions(authService);

        assertThat(result.getResponse().getHeaders(HttpHeaders.SET_COOKIE))
                .noneMatch(value -> value.startsWith("REFRESH_TOKEN="));
    }

    private MockHttpServletRequestBuilder logoutRequest() {
        // CSRF 정책은 별도 단계에서 검증한다.
        // 여기서는 유효한 CSRF 토큰이 있는 요청으로 로그아웃 연결을 확인한다.
        return post(LOGOUT_URL).with(csrf());
    }

    private Cookie refreshCookie(String token) {
        return new Cookie("REFRESH_TOKEN", token);
    }

    private void assertRefreshCookieDeleted(MvcResult result) {
        var cookies = result.getResponse()
                .getHeaders(HttpHeaders.SET_COOKIE)
                .stream()
                .filter(value -> value.startsWith("REFRESH_TOKEN="))
                .toList();

        assertThat(cookies).hasSize(1);

        assertThat(cookies.get(0))
                .startsWith("REFRESH_TOKEN=;")
                .contains("Path=/api/auth")
                .contains("Max-Age=0")
                .contains("HttpOnly")
                .contains("; Secure")
                .contains("SameSite=Lax")
                .doesNotContain("Domain=");
    }

    private void assertNoSessionOrRedirect(MvcResult result) {
        assertThat(result.getRequest().getSession(false)).isNull();
        assertThat(result.getResponse().getRedirectedUrl()).isNull();
    }
}