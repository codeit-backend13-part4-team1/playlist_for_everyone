package com.codeit.sb13.playlist_for_everyone.domain.auth.service;

import com.codeit.sb13.playlist_for_everyone.domain.auth.dto.TokenReissueResult;
import com.codeit.sb13.playlist_for_everyone.domain.auth.jwt.JwtProperties;
import com.codeit.sb13.playlist_for_everyone.domain.auth.jwt.JwtTokenProvider;
import com.codeit.sb13.playlist_for_everyone.domain.auth.repository.RefreshTokenRepository;
import com.codeit.sb13.playlist_for_everyone.domain.user.entity.Role;
import com.codeit.sb13.playlist_for_everyone.domain.user.entity.User;
import com.codeit.sb13.playlist_for_everyone.domain.user.service.UserService;
import com.codeit.sb13.playlist_for_everyone.global.exception.user.UserNotFoundException;
import io.jsonwebtoken.JwtException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.security.authentication.BadCredentialsException;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;


@ExtendWith(MockitoExtension.class)
class AuthServiceTest {
    private static final UUID USER_ID =
            UUID.fromString("11111111-1111-1111-1111-111111111111");


    // 실제 JWT가 아닌 서비스 테스트용 문자열이다.
    // JWT 자체의 검증은 JwtTokenProviderTest에서 한다.
    private static final String OLD_REFRESH_TOKEN = "old-refresh-token";
    private static final String NEW_REFRESH_TOKEN = "new-refresh-token";
    private static final String NEW_ACCESS_TOKEN = "new-access-token";

    private static final Duration REFRESH_VALIDITY = Duration.ofDays(7);

    @Mock
    private JwtTokenProvider jwtTokenProvider;

    @Mock
    private RefreshTokenRepository refreshTokenRepository;

    @Mock
    private UserService userService;

    @Mock
    private User user;

    private AuthService authService;

    @BeforeEach
    void setUp() {
        // Provider를 Mock으로 사용하므로 실제 서명 키는 필요하지 않다.
        JwtProperties properties = new JwtProperties(
                "unused-in-this-unit-test",
                Duration.ofMinutes(15),
                REFRESH_VALIDITY
        );

        authService = new AuthService(
                jwtTokenProvider,
                refreshTokenRepository,
                userService,
                properties
        );
    }

    @Test
    @DisplayName("유효한 Refresh Token을 교체하고 새 토큰 쌍과 사용자 정보를 반환한다")
    void 정상_재발급에_성공한다() {
        givenRegisteredToken();
        givenNewTokens();

        LocalDateTime createAt = LocalDateTime.of(2026, 10, 30, 10, 0);


        given(user.getId()).willReturn(USER_ID);
        given(user.getCreatedAt()).willReturn(createAt);
        given(user.getEmail()).willReturn("user@gmail.com");
        given(user.getName()).willReturn("테스트 사용자");
        given(user.getRole()).willReturn(Role.USER);


        given(refreshTokenRepository.rotate(
                USER_ID,
                OLD_REFRESH_TOKEN,
                NEW_REFRESH_TOKEN,
                REFRESH_VALIDITY
        )).willReturn(true);

        TokenReissueResult result = authService.reissue(OLD_REFRESH_TOKEN);

        assertThat(result.jwtDto().accessToken()).isEqualTo(NEW_ACCESS_TOKEN);
        assertThat(result.refreshToken()).isEqualTo(NEW_REFRESH_TOKEN);

        assertThat(result.jwtDto().userDto().id()).isEqualTo(USER_ID);
        assertThat(result.jwtDto().userDto().createdAt()).isEqualTo(createAt);
        assertThat(result.jwtDto().userDto().email()).isEqualTo("user@gmail.com");
        assertThat(result.jwtDto().userDto().name()).isEqualTo("테스트 사용자");
        assertThat(result.jwtDto().userDto().role()).isEqualTo(Role.USER);

        // 원자적 교체 메서드를 사용
        verify(refreshTokenRepository).rotate(
                USER_ID,
                OLD_REFRESH_TOKEN,
                NEW_REFRESH_TOKEN,
                REFRESH_VALIDITY
        );

        // save와 delete를 따로 호출하면 동시 요청을 안전하게 처리할 수 없다.
        verify(refreshTokenRepository, never())
                .save(any(),anyString(),any());
        verify(refreshTokenRepository, never()).delete(anyString());
    }

    @Test
    @DisplayName("JWT 검증에 실패하면 재발급을 거절한다")
    void 유효하지_않은_JWT를_거절한다() {
        given(jwtTokenProvider.parseRefreshToken(OLD_REFRESH_TOKEN))
                .willThrow(new JwtException("유효하지 않은 토큰"));

        assertThatThrownBy(()-> authService.reissue(OLD_REFRESH_TOKEN))
                .isInstanceOf(BadCredentialsException.class);

        // JWT 검증에 실패한 요청은 저장소와 사용자 조회까지 진행하지 않는다.
        verifyNoInteractions(refreshTokenRepository, userService);
        verify(jwtTokenProvider, never()).createAccessToken(any());
        verify(jwtTokenProvider, never()).createRefreshToken(any());
    }

    @Test
    @DisplayName("Redis에 등록되지 않은 Refresh Token의 재발급을 거절한다")
    void 미등록_토큰을_거절한다() {
        given(jwtTokenProvider.parseRefreshToken(OLD_REFRESH_TOKEN))
                .willReturn(USER_ID);
        given(refreshTokenRepository.findUserId(OLD_REFRESH_TOKEN))
                .willReturn(Optional.empty());
        assertThatThrownBy(()-> authService.reissue(OLD_REFRESH_TOKEN))
                .isInstanceOf(BadCredentialsException.class);

        verifyNoInteractions(userService);
        verifyNoTokenIssueOrRotation();
    }

    @Test
    @DisplayName("JWT 사용자와 Redis에 저장된 사용자가 다르면 재발급을 거절한다")
    void 토큰_소유자가_다르면_거절한다() {
        UUID anotherUserId = UUID.fromString("22222222-2222-2222-2222-222222222222");

        given(jwtTokenProvider.parseRefreshToken(OLD_REFRESH_TOKEN))
                .willReturn(USER_ID);

        given(refreshTokenRepository.findUserId(OLD_REFRESH_TOKEN))
                .willReturn(Optional.of(anotherUserId));
        assertThatThrownBy(() -> authService.reissue(OLD_REFRESH_TOKEN))
                .isInstanceOf(BadCredentialsException.class);

        verifyNoInteractions(userService);
        verifyNoTokenIssueOrRotation();
    }

    @Test
    @DisplayName("삭제된 사용자의 재발급을 거절한다")
    void 사용자가_없으면_거절한다() {
        given(jwtTokenProvider.parseRefreshToken(OLD_REFRESH_TOKEN))
                .willReturn(USER_ID);
        given(refreshTokenRepository.findUserId(OLD_REFRESH_TOKEN))
                .willReturn(Optional.of(USER_ID));
        given(userService.getById(USER_ID))
                .willThrow(new UserNotFoundException(
                        Map.of("userId", USER_ID)
                ));

        // 사용자 조회의 404 예외를 인증 실패로 변환하는 계약이다.
        assertThatThrownBy(() -> authService.reissue(OLD_REFRESH_TOKEN))
                .isInstanceOf(BadCredentialsException.class);

        verifyNoTokenIssueOrRotation();
    }

    @Test
    @DisplayName("잠긴 사용자의 재발급을 거절한다")
    void 잠긴_사용자를_거절한다() {
        givenRegisteredToken();
        given(user.isLocked()).willReturn(true);

        assertThatThrownBy(() -> authService.reissue(OLD_REFRESH_TOKEN))
                .isInstanceOf(BadCredentialsException.class);

        verifyNoTokenIssueOrRotation();
    }

    @Test
    @DisplayName("사전 조회 이후 토큰 교체에 실패하면 새 토큰을 반환하지 않는다")
    void 원자적_교체에_실패하면_거절한다() {
        givenRegisteredToken();
        givenNewTokens();

        // 사전 조회에는 성공했지만 다른 요청이 먼저 토큰을 사용한 상황이다.
        given(refreshTokenRepository.rotate(
                USER_ID,
                OLD_REFRESH_TOKEN,
                NEW_REFRESH_TOKEN,
                REFRESH_VALIDITY
        )).willReturn(false);

        assertThatThrownBy(() -> authService.reissue(OLD_REFRESH_TOKEN))
                .isInstanceOf(BadCredentialsException.class);

        verify(refreshTokenRepository).rotate(
                USER_ID,
                OLD_REFRESH_TOKEN,
                NEW_REFRESH_TOKEN,
                REFRESH_VALIDITY
        );

        // 교체 실패를 일반 저장으로 우회하면 안 된다.
        verify(refreshTokenRepository, never())
                .save(any(), anyString(), any());
    }

    @Test
    @DisplayName("로그아웃하면 전달받은 Refresh Token의 등록 정보를 삭제한다.")
    void 로그아웃하면_리프레시_토큰을_삭제한다() {
        authService.logout(OLD_REFRESH_TOKEN);

        // 해당 토큰으로 더 이상 재발급하지 못하도록 등록 정보를 삭제한다.
        verify(refreshTokenRepository).delete(OLD_REFRESH_TOKEN);

        // 로그아웃에서는 새 토큰을 발급하거나 사용자 정보를 조회하지 않는다.
        verifyNoInteractions(jwtTokenProvider, userService);
    }

    @Test
    @DisplayName("Redis 삭제에 실패하면 로그아웃을 성공으로 처리하지 않는다.")
    void 토큰_삭제_실패를_호출자에게_전달한다() {
        var exception = new DataAccessResourceFailureException("Redis 연결 실패");


        doThrow(exception)
                .when(refreshTokenRepository)
                .delete(OLD_REFRESH_TOKEN);

        // 토큰을 삭제하지 못했는데 정상 로그아웃한 것처럼 처리하지 않는다
        assertThatThrownBy(() -> authService.logout(OLD_REFRESH_TOKEN))
                .isSameAs(exception);
    }


    /**
     * JWT와 Redis에 동일한 사용자가 등록된 상황을 준비한다.
     */
    private void givenRegisteredToken() {
        given(jwtTokenProvider.parseRefreshToken(OLD_REFRESH_TOKEN))
                .willReturn(USER_ID);
        given(refreshTokenRepository.findUserId(OLD_REFRESH_TOKEN))
                .willReturn(Optional.of(USER_ID));
        given(userService.getById(USER_ID))
                .willReturn(user);
    }

    private void givenNewTokens() {
        given(jwtTokenProvider.createAccessToken(USER_ID))
                .willReturn(NEW_ACCESS_TOKEN);
        given(jwtTokenProvider.createRefreshToken(USER_ID))
                .willReturn(NEW_REFRESH_TOKEN);
    }

    /**
     *  검증 실패 시 새 토큰 발급이나 Redis 교체가 실행되지 않았는지 확인한다.
     */
    private void verifyNoTokenIssueOrRotation() {
        verify(jwtTokenProvider, never()).createAccessToken(any());
        verify(jwtTokenProvider, never()).createRefreshToken(any());
        verify(refreshTokenRepository, never())
                .rotate(any(), anyString(), anyString(), any());
    }
}