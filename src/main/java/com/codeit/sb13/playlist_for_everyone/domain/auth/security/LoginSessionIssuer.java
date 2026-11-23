package com.codeit.sb13.playlist_for_everyone.domain.auth.security;

import com.codeit.sb13.playlist_for_everyone.domain.auth.event.UserSessionRevokedEvent;
import com.codeit.sb13.playlist_for_everyone.domain.auth.jwt.JwtTokenProvider;
import com.codeit.sb13.playlist_for_everyone.domain.auth.repository.RefreshTokenRepository;
import com.codeit.sb13.playlist_for_everyone.domain.auth.repository.SessionStartResult;
import com.codeit.sb13.playlist_for_everyone.domain.auth.repository.UserSessionRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;

import java.time.Duration;
import java.util.Objects;
import java.util.UUID;

/**
 * 일반 로그인과 소셜 로그인이 같은 규칙으로 로그인 세션을 시작하도록 한다.
 *
 * 세션 시작 -> 토큰 발급 -> Refresh Token 저장 -> 교체된 이전 세션 종료 알림 순서로 처리
 * 응답 형식(JSON, 리다이렉트)과 쿠키 전달은 각 로그인 성공 핸들러가 담당한다.
 */
@Slf4j
public class LoginSessionIssuer {

    private final JwtTokenProvider jwtTokenProvider;
    private final RefreshTokenRepository refreshTokenRepository;
    private final UserSessionRepository userSessionRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final Duration refreshTokenValidity;

    /**
     * 로그인 세션은 Refresh Token과 같은 기간 동안 유지된다.
     */
    public LoginSessionIssuer(JwtTokenProvider jwtTokenProvider,
                              RefreshTokenRepository refreshTokenRepository,
                              UserSessionRepository userSessionRepository,
                              ApplicationEventPublisher eventPublisher,
                              Duration refreshTokenValidity) {

        this.jwtTokenProvider = Objects.requireNonNull(jwtTokenProvider);
        this.refreshTokenRepository = Objects.requireNonNull(refreshTokenRepository);
        this.userSessionRepository = Objects.requireNonNull(userSessionRepository);
        this.eventPublisher = Objects.requireNonNull(eventPublisher);
        this.refreshTokenValidity = Objects.requireNonNull(refreshTokenValidity);
        if (refreshTokenValidity.isZero() || refreshTokenValidity.isNegative()) {
            throw new IllegalArgumentException("Refresh Token 유효기간은 양수여야 합니다.");
        }

    }

    public LoginTokens issue(UUID userId) {
        Objects.requireNonNull(userId, "사용자 ID는 필수입니다.");

        // 기존 세션이 있으면 이 시점에 교체되어, 이전 로그인의 토큰은 현재 세션이 아니게 된다.
        SessionStartResult session = userSessionRepository.start(userId, refreshTokenValidity);

        // 두 토큰에 같은 세션 ID를 넣는다. 재발급할 때도 이 세션 ID를 유지한다.
        String accessToken = jwtTokenProvider.createAccessToken(userId, session.sessionId());
        String refreshToken = jwtTokenProvider.createRefreshToken(userId, session.sessionId());

        refreshTokenRepository.save(userId, refreshToken, refreshTokenValidity);

        // 저장까지 성공한 뒤에 이전 로그인을 끊는다. 로그인 처리는 트랜잭션 밖이므로 바로 발행한다.
        session.replaced().ifPresent(replacedSessionId -> publishSessionReplaced(userId, replacedSessionId));

        return new LoginTokens(accessToken, refreshToken);
    }

    private void publishSessionReplaced(UUID userId, UUID replacedSessionId) {
        try {
            eventPublisher.publishEvent(new UserSessionRevokedEvent(
                    userId,
                    replacedSessionId,
                    UserSessionRevokedEvent.RevokeReason.DUPLICATE_LOGIN
            ));
        } catch (RuntimeException exception) {
            log.error("이전 로그인 세션 종료 후속 처리에 실패했습니다. userId={}, sessionId={}",
                    userId, replacedSessionId, exception);

        }
    }

    /**
     * 새 로그인 세션에서 발급한 토큰.
     * Access Token은 응답 본문, Refresh Token은 쿠키로 전달한다.
     */
    public record LoginTokens(String accessToken, String refreshToken) {

    }
}
