package com.codeit.sb13.playlist_for_everyone.domain.auth.security;

import com.codeit.sb13.playlist_for_everyone.domain.auth.jwt.JwtTokenProvider;
import com.codeit.sb13.playlist_for_everyone.domain.auth.repository.RefreshTokenRepository;
import com.codeit.sb13.playlist_for_everyone.domain.auth.repository.UserSessionRepository;
import org.springframework.context.ApplicationEventPublisher;

import java.time.Duration;
import java.util.UUID;

/**
 * 일반 로그인과 소셜 로그인이 같은 규칙으로 로그인 세션을 시작하도록 한다.
 */
public class LoginSessionIssuer {

    public LoginSessionIssuer(JwtTokenProvider jwtTokenProvider,
                              RefreshTokenRepository refreshTokenRepository,
                              UserSessionRepository userSessionRepository,
                              ApplicationEventPublisher eventPublisher,
                              Duration refreshTokenValidity) {

    }

    public LoginTokens issue(UUID userId) {
        throw new UnsupportedOperationException("아직 구현되지 않았습니다.");
    }

    /**
     * 새 로그인 세션에서 발급한 토큰.
     * Access Token은 응답 본문, Refresh Token은 쿠키로 전달한다.
     */
    public record LoginTokens(String accessToken, String refreshToken) {

    }
}
