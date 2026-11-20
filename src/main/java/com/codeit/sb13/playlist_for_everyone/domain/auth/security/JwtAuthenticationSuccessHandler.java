package com.codeit.sb13.playlist_for_everyone.domain.auth.security;

import com.codeit.sb13.playlist_for_everyone.domain.auth.dto.response.JwtDto;
import com.codeit.sb13.playlist_for_everyone.domain.user.dto.response.UserDto;
import com.codeit.sb13.playlist_for_everyone.domain.user.service.UserService;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseCookie;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.UUID;

/**
 * 로그인 인증 성공 후 토큰 발급과 HTTP 응답 작성을 담당한다.
 *
 * 비밀번호 검증은 AuthenticationManager가 수행한다.
 */
public class JwtAuthenticationSuccessHandler implements AuthenticationSuccessHandler {

    private final RefreshTokenCookieFactory refreshTokenCookieFactory;

    private final LoginSessionIssuer loginSessionIssuer;
    private final UserService userService;
    private final ObjectMapper objectMapper;

    /**
     * 로그인 세션 시작, Refresh Token 발급·저장과 쿠키 전달에 필요한 의존성을 전달받는다.
     *
     * 실제 서버 연결시 Provider와 같은 Refresh Token 유효기간을 전달해야 한다.
     * 로그인 세션도 같은 기간 동안 유지된다.
     */

    public JwtAuthenticationSuccessHandler(LoginSessionIssuer loginSessionIssuer,
                                           UserService userService,
                                           ObjectMapper objectMapper,
                                           RefreshTokenCookieFactory refreshTokenCookieFactory) {
        this.loginSessionIssuer = Objects.requireNonNull(loginSessionIssuer);
        this.userService = Objects.requireNonNull(userService);
        this.objectMapper = Objects.requireNonNull(objectMapper);
        this.refreshTokenCookieFactory = Objects.requireNonNull(refreshTokenCookieFactory);
    }

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response, Authentication authentication) throws IOException, ServletException {

        // 클라이언트가 보낸 ID가 아니라 인증이 완료된 사용자의 ID를 사용한다.
        SecurityUser principal = (SecurityUser) authentication.getPrincipal();
        UUID userId = principal.getUserId();

        // 응답에 필요한 사용자 정보를 먼저 준비 한다.
        // 여기서 실패하면 기존 로그인 세션을 교체하지 않는다.
        UserDto userDto = userService.getUserDtoById(userId);

        // 세션 시작, 토큰 발급, Refresh Token 저장, 이전 세션 종료 알림
        LoginSessionIssuer.LoginTokens tokens = loginSessionIssuer.issue(userId);
        ResponseCookie refreshCookie = refreshTokenCookieFactory.create(tokens.refreshToken());

        // Access Token은 JSON으로, Refresh Token은 HttpOnly 쿠키로 전달한다.
        JwtDto jwtDto = new JwtDto(userDto, tokens.accessToken());

        // Redis 저장 이후에 응답 작성
        response.setStatus(HttpServletResponse.SC_OK);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8);
        response.addHeader(HttpHeaders.SET_COOKIE, refreshCookie.toString());

        // 로그인 성공 응답은 세션이나 리다이렉트 없이 JSON으로 반환한다.
        objectMapper.writeValue(response.getWriter(), jwtDto);
    }

}
