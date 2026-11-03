package com.codeit.sb13.playlist_for_everyone.domain.auth.security;

import com.codeit.sb13.playlist_for_everyone.domain.auth.service.AuthService;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.jspecify.annotations.Nullable;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.logout.LogoutSuccessHandler;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.UnsupportedEncodingException;

public class JwtLogoutSuccessHandler implements LogoutSuccessHandler {


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
     * 저장소 오류가 발생하면 성공 응답 대신 오류 응답을 반환한다.
     */
    @Override
    public void onLogoutSuccess(HttpServletRequest request,
                                HttpServletResponse response,
                                @Nullable Authentication authentication) throws IOException, ServletException {
        // RED: 다음 단계에서 토큰 삭제와 HTTP 응답 구현
        throw new UnsupportedOperationException("로그아웃 응답 구현 예정");

    }
}
