package com.codeit.sb13.playlist_for_everyone.domain.auth.security;


import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;

/**
 * 로그인 실패를 공통 JSON 오류 응답으로 반환한다.
 *
 * 내부 예외 메시지를 그대로 노출하지 않는다.
 */
public class JwtAuthenticationFailureHandler implements AuthenticationFailureHandler {

    private final ObjectMapper objectMapper;

    public JwtAuthenticationFailureHandler(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void onAuthenticationFailure(HttpServletRequest request, HttpServletResponse response, AuthenticationException exception) throws IOException, ServletException {
        // RED: 그린 단계에서 401과 공통 오류 응답을 작성한다.
        throw new UnsupportedOperationException("로그인 실패 처리 미구현");
    }
}
