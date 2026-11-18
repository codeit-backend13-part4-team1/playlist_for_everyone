package com.codeit.sb13.playlist_for_everyone.domain.auth.security;

import com.codeit.sb13.playlist_for_everyone.domain.auth.service.AuthService;
import com.codeit.sb13.playlist_for_everyone.global.dto.response.ApiErrorResponse;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseCookie;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.logout.LogoutSuccessHandler;
import org.springframework.web.util.WebUtils;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;

@Slf4j
public class JwtLogoutSuccessHandler implements LogoutSuccessHandler {

    private static final String REFRESH_COOKIE_NAME = "REFRESH_TOKEN";
    private static final String REFRESH_COOKIE_PATH = "/api/auth";


    private final AuthService authService;
    private final ObjectMapper objectMapper;
    private final boolean refreshCookieSecure;


    public JwtLogoutSuccessHandler(AuthService authService, ObjectMapper objectMapper, boolean refreshCookieSecure) {
        this.authService = authService;
        this.objectMapper = objectMapper;
        this.refreshCookieSecure = refreshCookieSecure;
    }


    /**
     * Refresh Token 등록 정보를 삭제하고 로그아웃 응답을 작성한다.
     *
     * 정상 처리 시 쿠키를 만료시키고 204를 반환한다.
     * 저장소 삭제 실패 시에는 성공으로 처리하지 않고 500을 반환한다.
     */
    @Override
    public void onLogoutSuccess(HttpServletRequest request,
                                HttpServletResponse response,
                                @Nullable Authentication authentication) throws IOException, ServletException {

        Cookie refreshCookie = WebUtils.getCookie(request, REFRESH_COOKIE_NAME);

        // 쿠키가 없거나 비어 있으면 삭제할 토큰이 없는 상태로 처리한다.
        if (refreshCookie != null && !refreshCookie.getValue().isBlank()) {
            try {
                authService.logout(refreshCookie.getValue());
            } catch (DataAccessException exception) {
                log.error("로그아웃 중 Refresh Token 삭제 실패. exceptionType={}", exception.getClass().getSimpleName());

                writeFailureResponse(response);
                return;
            }

        }

        // Redis 삭제에 성공했거나 삭제할 토큰이 없을때 쿠키를 정리한다.
        expireRefreshCookie(response);

        // API 응답이므로 리다이렉트와 응답 본문 없이 종료한다.
        response.setStatus(HttpServletResponse.SC_NO_CONTENT);

    }

    /**
     * 기존 쿠키와 같은 이름 경로를 사용해 브라우저에서 제거하도록 한다.
     */
    private void expireRefreshCookie(HttpServletResponse response) {
        ResponseCookie expiredCookie = ResponseCookie
                .from(REFRESH_COOKIE_NAME, "")
                .httpOnly(true)
                .secure(refreshCookieSecure)
                .sameSite("Lax")
                .path(REFRESH_COOKIE_PATH)
                .maxAge(0)
                .build();

        // 다른 Set-Cookie 응답이 있더라도 덮어쓰지 않는다.
        response.addHeader(
                HttpHeaders.SET_COOKIE,
                expiredCookie.toString()
        );
    }

    /**
     * 필터에서 발생한 저장소 오류를 공통 JSON 형식으로 반환한다.
     */
    private void writeFailureResponse(HttpServletResponse response) throws IOException {
        ApiErrorResponse errorResponse = new ApiErrorResponse(
                "LogoutFailedException",
                "로그아웃 처리에 실패했습니다. 잠시 후 다시 시도해 주세요.",
                Map.of()
        );

        response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());

        objectMapper.writeValue(response.getWriter(), errorResponse);

    }

}
