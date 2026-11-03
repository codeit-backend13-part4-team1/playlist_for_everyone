package com.codeit.sb13.playlist_for_everyone.domain.auth.security;

import com.codeit.sb13.playlist_for_everyone.domain.auth.dto.response.JwtDto;
import com.codeit.sb13.playlist_for_everyone.domain.auth.jwt.JwtTokenProvider;
import com.codeit.sb13.playlist_for_everyone.domain.auth.repository.RefreshTokenRepository;
import com.codeit.sb13.playlist_for_everyone.domain.user.dto.response.UserDto;
import com.codeit.sb13.playlist_for_everyone.domain.user.entity.User;
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
import java.time.Duration;
import java.util.Objects;

/**
 * 로그인 인증 성공 후 토큰 발급과 HTTP 응답 작성을 담당한다.
 *
 * 비밀번호 검증은 AuthenticationManager가 수행한다.
 */
public class JwtAuthenticationSuccessHandler implements AuthenticationSuccessHandler {

    private static final String REFRESH_COOKIE_NAME = "REFRESH_TOKEN";
    private static final String REFRESH_COOKIE_PATH = "/api/auth";

    private final JwtTokenProvider jwtTokenProvider;
    private final UserService userService;
    private final ObjectMapper objectMapper;

    private final RefreshTokenRepository refreshTokenRepository;
    private final Duration refreshTokenValidity;
    private final boolean refreshCookieSecure;

    /**
     * Refresh Token 발급·저장과 쿠키 전달에 필요한 의존성을 받는다.
     *
     * 실제 서버 연결시 Provider와 같은 Refresh Token 유효기간을 전달해야 한다.
     */

    public JwtAuthenticationSuccessHandler(JwtTokenProvider jwtTokenProvider,
                                           UserService userService,
                                           ObjectMapper objectMapper,
                                           RefreshTokenRepository refreshTokenRepository,
                                           Duration refreshTokenValidity,
                                           boolean refreshCookieSecure) {
        this.jwtTokenProvider = Objects.requireNonNull(jwtTokenProvider);
        this.userService = Objects.requireNonNull(userService);
        this.objectMapper = Objects.requireNonNull(objectMapper);
        this.refreshTokenRepository = Objects.requireNonNull(refreshTokenRepository);
        this.refreshTokenValidity = Objects.requireNonNull(refreshTokenValidity);

        if (refreshTokenValidity.isZero() || refreshTokenValidity.isNegative()) {
            throw new IllegalArgumentException("Refresh Token 유효기간은 양수여야 합니다.");
        }
        this.refreshCookieSecure = refreshCookieSecure;
    }

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response, Authentication authentication) throws IOException, ServletException {

        // 클라이언트가 보낸 ID가 아니라 인증이 완료된 사용자의 ID를 사용한다.
        SecurityUser principal = (SecurityUser) authentication.getPrincipal();

        // 응답에 필요한, 가입 시각 등의 사용자 정보로 조회한다.
        User user = userService.getById(principal.getUserId());

        String accessToken = jwtTokenProvider.createAccessToken(principal.getUserId());

        String refreshToken = jwtTokenProvider.createRefreshToken(principal.getUserId());

        ResponseCookie refreshCookie = createRefreshCookie(refreshToken);

        refreshTokenRepository.save(principal.getUserId(), refreshToken, refreshTokenValidity);

        UserDto userDto = new UserDto(
                user.getId(),
                user.getCreatedAt(),
                user.getEmail(),
                user.getName(),
                null, // 프로필 이미지 연동 전에는 null을 반환한다.
                user.getRole(),
                user.isLocked()
        );

        // Access Token은 JSON으로, Refresh Token은 HttpOnly 쿠키로 전달한다.
        JwtDto jwtDto = new JwtDto(userDto, accessToken);

        // Redis 저장 이후에 응답 작성
        response.setStatus(HttpServletResponse.SC_OK);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8);

        response.addHeader(HttpHeaders.SET_COOKIE, refreshCookie.toString());

        // 로그인 성공 응답은 세션이나 리다이렉트 없이 JSON으로 반환한다.
        objectMapper.writeValue(response.getWriter(), jwtDto);
    }

    /**
     * 브라우저가 보관할 Refresh Token 쿠키를 생성한다.
     *
     * 쿠키 생성과 HTTP 응답을 추가로 분리하여,
     * Redis 저장이 실패하면 쿠키가 전달되지 않도록 한다.
     */

    private ResponseCookie createRefreshCookie(String refreshToken) {
        return ResponseCookie.from(REFRESH_COOKIE_NAME, refreshToken)
                // JavaScript에서 쿠키 값을 읽지 못하게 한다.
                .httpOnly(true)
                // HTTPS 환경에서는 true, 로컬 HTTP에서는 false로 설정한다.
                .secure(refreshCookieSecure)
                // 현재 같은 사이트에서 사용하는 프론트/API 구성을 기준으로 한다.
                .sameSite("Lax")
                // 토큰 재발급과 로그아웃 경로에서 사용할 수 있도록 한다.
                .path(REFRESH_COOKIE_PATH)
                // 브라우저의 쿠키 보관 기간이다.
                .maxAge(refreshTokenValidity)
                // Domain은 지정하지 않아 현재 호스트에 한정한다.
                .build();
    }


}
