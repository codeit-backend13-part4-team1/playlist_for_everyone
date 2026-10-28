package com.codeit.sb13.playlist_for_everyone.domain.auth.security;

import com.codeit.sb13.playlist_for_everyone.domain.user.entity.Role;
import com.codeit.sb13.playlist_for_everyone.global.config.PasswordEncoderConfig;
import com.codeit.sb13.playlist_for_everyone.global.config.SecurityAuthenticationConfig;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.authentication.BadCredentialsException;


import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("이메일, 비밀번호 인증")
class AuthenticationManagerTest {

    private static final UUID USER_ID = UUID.randomUUID();
    private static final String EMAIL = "test@gmail.com";
    private static final String PASSWORD = "correct-password";

    @Mock
    private CustomUserDetailsService userDetailsService;

    private PasswordEncoder passwordEncoder;
    private AuthenticationManager authenticationManager;

    @BeforeEach
    void setUp() {
        // 비밀번호 검증은 Mock이 아닌 프로젝트의 실제 BCrypt를 사용한다.
        passwordEncoder = new PasswordEncoderConfig().passwordEncoder();

        // 실제 운영 설정 메서드로 Manager와 Provider를 구성한다.
        // Spring 컨텍스트나 DB는 실행하지 않는다.
        authenticationManager = new SecurityAuthenticationConfig().authenticationManager(
                userDetailsService,
                passwordEncoder
        );
    }

    @Test
    @DisplayName("올바른 비밀번호면 인증하고 사용자 정보와 권한을 반환한다")
    void 올바른_비밀번호면_인증에_성공한다() {
        SecurityUser securityUser = createSecurityUser(false);

        given(userDetailsService.loadUserByUsername(EMAIL))
                .willReturn(securityUser);

        // 인증 전 요청이므로 unauthenticated 팩토리를 사용한다.
        Authentication request = UsernamePasswordAuthenticationToken.unauthenticated(
                EMAIL,
                PASSWORD
        );

        Authentication result = authenticationManager.authenticate(request);

        assertThat(result.isAuthenticated()).isTrue();
        assertThat(result.getPrincipal()).isInstanceOf(SecurityUser.class);
        assertThat(result.getAuthorities())
                .extracting(GrantedAuthority::getAuthority)
                .containsExactlyInAnyOrder(
                        "ROLE_USER",
                        "FACTOR_PASSWORD"
                );

        SecurityUser principal = (SecurityUser) result.getPrincipal();

        assertThat(result.getCredentials()).isNull();
        assertThat(principal.getPassword()).isNull();
        assertThat(principal.getUserId()).isEqualTo(USER_ID);
        assertThat(principal.getUsername()).isEqualTo(EMAIL);

        verify(userDetailsService).loadUserByUsername(EMAIL);
    }

    @Test
    @DisplayName("비밀번호가 틀리면 인증에 실패한다.")
    void 잘못된_비밀번호면_인증에_실패한다() {
        given(userDetailsService.loadUserByUsername(EMAIL))
                .willReturn(createSecurityUser(false));

        Authentication request = UsernamePasswordAuthenticationToken.unauthenticated(
                EMAIL,
                "wrong-password"
        );

        assertThatThrownBy(() -> authenticationManager.authenticate(request))
                .isInstanceOf(BadCredentialsException.class);
    }

    @Test
    @DisplayName("없는 이메일도 비밀번호 불일치와 같은 종류의 예외를 던진다")
    void 없는_이메일이면_인증에_실패한다() {
        given(userDetailsService.loadUserByUsername(EMAIL))
                .willThrow(new UsernameNotFoundException(
                        "사용자 인증 정보를 찾을 수 없습니다."
                ));

        Authentication request =
                UsernamePasswordAuthenticationToken.unauthenticated(
                        EMAIL,
                        PASSWORD
                );

        assertThatThrownBy(() ->
                authenticationManager.authenticate(request))
                .isInstanceOf(BadCredentialsException.class);
    }

    @Test
    @DisplayName("잠긴 계정은 비밀번호가 맞아도 인증을 거절한다")
    void 잠긴_계정이면_인증에_실패한다() {
        given(userDetailsService.loadUserByUsername(EMAIL))
                .willReturn(createSecurityUser(true));

        Authentication request =
                UsernamePasswordAuthenticationToken.unauthenticated(
                        EMAIL,
                        PASSWORD
                );

        assertThatThrownBy(() ->
                authenticationManager.authenticate(request))
                .isInstanceOf(LockedException.class);
    }

    private SecurityUser createSecurityUser(boolean locked) {
        return new SecurityUser(
                USER_ID,
                EMAIL,
                passwordEncoder.encode(PASSWORD),
                Role.USER,
                locked
        );
    }
}
