package com.codeit.sb13.playlist_for_everyone.domain.auth.jwt;

import com.codeit.sb13.playlist_for_everyone.domain.user.service.UserService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * 요청의 Bearer 토큰을 검증하고 Spring Security 인증 정보를 설정한다.
 *
 * URL별 접근 권한 판단은 SecurityFilterChain에서 담당한다.
 */

public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtTokenProvider jwtTokenProvider;
    private final UserService userService;
    private final AuthenticationEntryPoint authenticationEntryPoint;

    public JwtAuthenticationFilter(JwtTokenProvider jwtTokenProvider, UserService userService, AuthenticationEntryPoint authenticationEntryPoint) {
        this.jwtTokenProvider = jwtTokenProvider;
        this.userService = userService;
        this.authenticationEntryPoint = authenticationEntryPoint;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {
        // RED 단계: HTTP 요청과 JWT 인증 연결은 아직 구현하지 않는다.
        throw new UnsupportedOperationException("JWT 인증 필터 구현 전");
    }
}
