package com.codeit.sb13.playlist_for_everyone.domain.auth.security;


import com.codeit.sb13.playlist_for_everyone.domain.auth.event.UserSessionRevokedEvent;
import com.codeit.sb13.playlist_for_everyone.domain.auth.jwt.JwtTokenProvider;
import com.codeit.sb13.playlist_for_everyone.domain.auth.repository.RefreshTokenRepository;
import com.codeit.sb13.playlist_for_everyone.domain.auth.repository.SessionStartResult;
import com.codeit.sb13.playlist_for_everyone.domain.auth.repository.UserSessionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.time.Duration;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
@DisplayName("로그인 세션 시작")
class LoginSessionIssuerTest {

    private static final UUID USER_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID SESSION_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID REPLACED_SESSION_ID = UUID.fromString("33333333-3333-3333-3333-333333333333");
    private static final Duration REFRESH_TOKEN_VALIDITY = Duration.ofDays(3);


    @Mock
    private JwtTokenProvider jwtTokenProvider;

    @Mock
    private RefreshTokenRepository refreshTokenRepository;

    @Mock
    private UserSessionRepository userSessionRepository;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    private LoginSessionIssuer issuer;

    @BeforeEach
    void setUp() {
        issuer = new LoginSessionIssuer(jwtTokenProvider, refreshTokenRepository,
                userSessionRepository, eventPublisher, REFRESH_TOKEN_VALIDITY);
    }

    @Test
    @DisplayName("Refresh Token 유효기간으로 세션을 시작하고 두 토큰에 같은 세션 ID를 넣는다.")
    void Refresh_Token_유효기간으로_세션을_시작하고_두_토큰에_같은_세션_ID를_넣는다() {
        givenSessionStarted(null);

        LoginSessionIssuer.LoginTokens tokens = issuer.issue(USER_ID);

        verify(userSessionRepository).start(USER_ID, REFRESH_TOKEN_VALIDITY);
        assertThat(tokens.accessToken()).isEqualTo("access-token");
        assertThat(tokens.refreshToken()).isEqualTo("refresh-token");
    }

    @Test
    @DisplayName("Refresh Token을 유효기간과 함께 저장한다.")
    void Refresh_Token을_유효기간과_함께_저장한다() {
        givenSessionStarted(null);

        issuer.issue(USER_ID);

        verify(refreshTokenRepository).save(USER_ID, "refresh-token", REFRESH_TOKEN_VALIDITY);
    }

    @Test
    @DisplayName("기존 로그인이 없으면 세션 종료 이벤트를 발행하지 않는다.")
    void 기존_로그인이_없으면_세션_종료_이벤트를_발행하지_않는다() {
        givenSessionStarted(null);

        issuer.issue(USER_ID);

        verifyNoInteractions(eventPublisher);
    }

    @Test
    @DisplayName("기존 로그인이 있으면 교체된 세션의 종료 이벤트를 발행한다.")
    void 기존_로그인이_있으면_교체된_세션의_종료_이벤트를_발행한다() {
        givenSessionStarted(REPLACED_SESSION_ID);

        issuer.issue(USER_ID);

        verify(eventPublisher).publishEvent(new UserSessionRevokedEvent(
                USER_ID, REPLACED_SESSION_ID, UserSessionRevokedEvent.RevokeReason.DUPLICATE_LOGIN));
    }

    @Test
    @DisplayName("세션 종료 후속 처리가 실패해도 토큰을 발급한다.")
    void 세션_종료_후속_처리가_실패해도_토큰을_발급한다() {
        givenSessionStarted(REPLACED_SESSION_ID);
        willThrow(new IllegalStateException("후속 처리 실패"))
                .given(eventPublisher).publishEvent(any(Object.class));

        LoginSessionIssuer.LoginTokens tokens = issuer.issue(USER_ID);

        assertThat(tokens.refreshToken()).isEqualTo("refresh-token");
    }

    @Test
    @DisplayName("Refresh Token 저장에 실패하면 예외를 전달하고 세션 종료 이벤트를 발행하지 않는다.")
    void Refresh_Token_저장에_실패하면_예외를_전달하고_세션_종료_이벤트를_발행하지_않는다() {
        givenSessionStarted(REPLACED_SESSION_ID);
        willThrow(new IllegalStateException("Redis 저장 실패"))
                .given(refreshTokenRepository).save(USER_ID, "refresh-token", REFRESH_TOKEN_VALIDITY);

        assertThatThrownBy(() -> issuer.issue(USER_ID))
                .isInstanceOf(IllegalStateException.class);

        verifyNoInteractions(eventPublisher);
    }

    @Test
    @DisplayName("Refresh Token 유효기간이 0 이하이면 생성할 수 없다.")
    void Refresh_Token_유효기간이_0_이하이면_생성할_수_없다() {
        assertThatThrownBy(() -> new LoginSessionIssuer(jwtTokenProvider, refreshTokenRepository,
                userSessionRepository, eventPublisher, Duration.ZERO))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private void givenSessionStarted(UUID replacedSessionId) {
        given(userSessionRepository.start(USER_ID, REFRESH_TOKEN_VALIDITY))
                .willReturn(new SessionStartResult(SESSION_ID, replacedSessionId));
        given(jwtTokenProvider.createAccessToken(USER_ID, SESSION_ID)).willReturn("access-token");
        given(jwtTokenProvider.createRefreshToken(USER_ID, SESSION_ID)).willReturn("refresh-token");
    }

}
