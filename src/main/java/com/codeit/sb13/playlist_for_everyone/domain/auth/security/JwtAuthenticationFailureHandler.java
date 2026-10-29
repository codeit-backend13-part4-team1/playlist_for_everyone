package com.codeit.sb13.playlist_for_everyone.domain.auth.security;


import com.codeit.sb13.playlist_for_everyone.global.dto.response.ApiErrorResponse;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;

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

        // 이메일 미존재, 비밀번호 불일치, 계정 잠금의 외부 응답을 통일한다.
        ApiErrorResponse errorResponse = new ApiErrorResponse(
                "BadCredentialsException",
                "로그인에 실패했습니다.",
                Map.of()
        );

        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());

        objectMapper.writeValue(response.getWriter(), errorResponse);
    }
}
