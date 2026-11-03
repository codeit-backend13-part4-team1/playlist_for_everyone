package com.codeit.sb13.playlist_for_everyone.domain.auth.security;


import com.codeit.sb13.playlist_for_everyone.domain.auth.service.AuthService;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;


@ExtendWith(MockitoExtension.class)
class JwtLogoutSuccessHandlerTest {

    private static final String REFRESH_TOKEN = "test-refresh-token";

    @Mock
    private AuthService  authService;

    private JwtLogoutSuccessHandler handler;
    private MockHttpServletRequest request;
    private MockHttpServletResponse response;

    @BeforeEach
    void setUp() {
        // Secure = true로 설정한 경우 삭제 쿠키에도 적용되는 지 확인
        handler = new JwtLogoutSuccessHandler(
                authService,
                new ObjectMapper(),
                true
        );

        request = new MockHttpServletRequest();
        response = new MockHttpServletResponse();
    }

    @Test
    @DisplayName("로그아웃 시 토큰 등록 정보를 삭제하고 쿠키를 만료시킨다")
    void 토큰과_쿠키를_삭제한다() throws Exception {
        request.setCookies(new Cookie("REFRESH_TOKEN", REFRESH_TOKEN));

        // Access Token 인증 정보 없이도 쿠키를 기준으로 정리한다.
        handler.onLogoutSuccess(request, response, null);

        verify(authService).logout(REFRESH_TOKEN);

        assertLogoutSuccess();
        assertRefreshCookieDeleted();
    }

    @Test
    @DisplayName("Refresh Token 쿠키가 없으면 서비스 호출 없이 로그아웃 응답을 반환한다")
    void 쿠키가_없어도_로그아웃한다() throws Exception {
        handler.onLogoutSuccess(request, response, null);

        // 삭제할 토큰이 없으므로 Redis 삭제를 요청하지 않는다.
        verifyNoInteractions(authService);

        assertLogoutSuccess();
        assertRefreshCookieDeleted();
    }

    @Test
    @DisplayName("Refresh Token 쿠키가 비어 있으면 서비스 호출 없이 로그아웃 응답을 반환한다")
    void 빈_쿠키도_정리한다() throws Exception {
        request.setCookies(new Cookie("REFRESH_TOKEN", ""));

        handler.onLogoutSuccess(request, response, null);

        verifyNoInteractions(authService);

        assertLogoutSuccess();
        assertRefreshCookieDeleted();
    }

    @Test
    @DisplayName("Redis 삭제에 실패하면 성공 대신 500 JSON 응답을 반환한다")
    void 저장소_장애를_실패로_반환한다() throws Exception {
        request.setCookies(new Cookie("REFRESH_TOKEN", REFRESH_TOKEN));

        doThrow(new DataAccessResourceFailureException("Redis 연결 실패"))
                .when(authService)
                .logout(REFRESH_TOKEN);

        handler.onLogoutSuccess(request, response, null);

        verify(authService).logout(REFRESH_TOKEN);

        assertThat(response.getStatus()).isEqualTo(500);
        assertThat(response.getContentType()).isNotNull();
        assertThat(
                MediaType.parseMediaType(response.getContentType())
                        .isCompatibleWith(MediaType.APPLICATION_JSON)
        ).isTrue();

        // 공통 오류 응답의 필드가 포함되는지 확인한다.
        var body = new ObjectMapper()
                .readTree(response.getContentAsString());

        assertThat(body.hasNonNull("exceptionName")).isTrue();
        assertThat(body.hasNonNull("message")).isTrue();

        // 실패 시에는 쿠키를 유지해 같은 토큰으로 삭제를 재시도할 수 있게 한다.
        assertThat(response.getHeaders(HttpHeaders.SET_COOKIE))
                .noneMatch(value -> value.startsWith("REFRESH_TOKEN="));

        // 토큰 원문이나 내부 장애 상세를 응답에 노출하지 않는다.
        assertThat(response.getContentAsString())
                .doesNotContain(REFRESH_TOKEN, "Redis 연결 실패");

        assertThat(response.getRedirectedUrl()).isNull();
    }

    private void assertLogoutSuccess() throws Exception {
        assertThat(response.getStatus()).isEqualTo(204);
        assertThat(response.getContentAsString()).isEmpty();

        // API 로그아웃이므로 페이지 이동이나 HTTP 세션을 만들지 않는다.
        assertThat(response.getRedirectedUrl()).isNull();
        assertThat(request.getSession(false)).isNull();
    }

    private void assertRefreshCookieDeleted() {
        var cookies = response.getHeaders(HttpHeaders.SET_COOKIE)
                .stream()
                .filter(value -> value.startsWith("REFRESH_TOKEN="))
                .toList();

        assertThat(cookies).hasSize(1);

        // 기존 쿠키와 이름·Path를 맞추고 Max-Age=0으로 삭제한다.
        assertThat(cookies.get(0))
                .startsWith("REFRESH_TOKEN=;")
                .contains("Path=/api/auth")
                .contains("Max-Age=0")
                .contains("HttpOnly")
                .contains("; Secure")
                .contains("SameSite=Lax")
                .doesNotContain("Domain=");
    }

}