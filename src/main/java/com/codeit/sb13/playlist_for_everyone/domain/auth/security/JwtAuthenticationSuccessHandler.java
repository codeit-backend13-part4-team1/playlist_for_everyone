package com.codeit.sb13.playlist_for_everyone.domain.auth.security;

import com.codeit.sb13.playlist_for_everyone.domain.auth.jwt.JwtTokenProvider;
import com.codeit.sb13.playlist_for_everyone.domain.user.service.UserService;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;

/**
 * 로그인 인증 성공 후 토큰 발급과 HTTP 응답 작성을 담당한다.
 *
 * 비밀번호 검증은 AuthenticationManager가 수행한다.
 */
public class JwtAuthenticationSuccessHandler implements AuthenticationSuccessHandler {

    private final JwtTokenProvider jwtTokenProvider;
    private final UserService userService;
    private final ObjectMapper objectMapper;


    public JwtAuthenticationSuccessHandler(JwtTokenProvider jwtTokenProvider, UserService userService, ObjectMapper objectMapper) {
        this.jwtTokenProvider = jwtTokenProvider;
        this.userService = userService;
        this.objectMapper = objectMapper;
    }

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response, Authentication authentication) throws IOException, ServletException {
        // RED: 컴파일은 가능하지만 동작은 아직 구현하지 않는다.
        throw new UnsupportedOperationException("로그인 서공 처리 미구현");
    }
}
