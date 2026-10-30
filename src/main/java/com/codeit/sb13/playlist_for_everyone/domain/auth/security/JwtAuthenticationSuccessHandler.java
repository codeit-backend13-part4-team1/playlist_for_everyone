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
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

/**
 * 로그인 인증 성공 후 토큰 발급과 HTTP 응답 작성을 담당한다.
 *
 * 비밀번호 검증은 AuthenticationManager가 수행한다.
 */
public class JwtAuthenticationSuccessHandler implements AuthenticationSuccessHandler {

    private final JwtTokenProvider jwtTokenProvider;
    private final UserService userService;
    private final ObjectMapper objectMapper;

    /**
     * Refresh Token 저장소와 쿠키 정책을 전달받는 생성자.
     *
     * RED 단계에서는 기존 로그인 처리만 수행한다.
     * 저장소 연결과 쿠키 작성은 GREEN 단계에서 구현한다.
     */

    public JwtAuthenticationSuccessHandler(JwtTokenProvider jwtTokenProvider,
                                           UserService userService,
                                           ObjectMapper objectMapper,
                                           RefreshTokenRepository refreshTokenRepository,
                                           Duration refreshTokenValidity,
                                           boolean refreshCookieSecure) {
        this(jwtTokenProvider, userService, objectMapper);
    }


    public JwtAuthenticationSuccessHandler(JwtTokenProvider jwtTokenProvider, UserService userService, ObjectMapper objectMapper) {
        this.jwtTokenProvider = jwtTokenProvider;
        this.userService = userService;
        this.objectMapper = objectMapper;
    }

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response, Authentication authentication) throws IOException, ServletException {
        // 인증 과정에서 생성된 사용자 정보를 가져온다.
        // 요청 파라미터의 사용자 ID를 신뢰하지 않는다.
        SecurityUser principal = (SecurityUser) authentication.getPrincipal();

        // SecurityUser에 없는 이름, 가입 시각 등 응답 정보를 조회한다.
        User user = userService.getById(principal.getUserId());

        String accessToken = jwtTokenProvider.createAccessToken(principal.getUserId());

        // 엔티티 전체를 반환하지 않고 공개할 필드만 DTO에 담는다.
        // 비밀번호는 해당 DTO에 포함되지 않는다.
        UserDto userDto = new UserDto(
                user.getId(),
                user.getCreatedAt(),
                user.getEmail(),
                user.getName(),
                null, // 프로필 이미지 연동 전에는 기존 회원가입 응답과 동일하게 null 처리
                user.getRole(),
                user.isLocked()
        );

        JwtDto jwtDto = new JwtDto(userDto, accessToken);

        // REST 로그인 성공 응답이므로 리다이렉트 대신 JSON을 반환한다.
        // Writer를 얻기 전에 인코딩을 지정해야 한글이 올바르게 출력된다.
        response.setStatus(HttpServletResponse.SC_OK);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8);


        // 세션 인증 정보를 저장하지 않고 응답 본문만 작성한다.
        objectMapper.writeValue(response.getWriter(), jwtDto);
    }
}
