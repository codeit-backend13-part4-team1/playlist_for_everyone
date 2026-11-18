package com.codeit.sb13.playlist_for_everyone.domain.auth.security;

import com.codeit.sb13.playlist_for_everyone.domain.auth.entity.TemporaryPassword;
import com.codeit.sb13.playlist_for_everyone.domain.auth.entity.TemporaryPasswordStatus;
import com.codeit.sb13.playlist_for_everyone.domain.auth.repository.TemporaryPasswordRepository;
import com.codeit.sb13.playlist_for_everyone.domain.user.entity.Role;
import com.codeit.sb13.playlist_for_everyone.global.config.PasswordEncoderConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
@DisplayName("임시 비밀번호 인증")
class TemporaryPasswordAuthenticationProviderTest {

    private static final UUID USER_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final String EMAIL = "user@test.com";
    private static final String TEMPORARY_PASSWORD = "Ab3!Cd4@Ef5#";

    @Mock
    private CustomUserDetailsService userDetailsService;

    @Mock
    private TemporaryPasswordRepository temporaryPasswordRepository;

    private final PasswordEncoder passwordEncoder = new PasswordEncoderConfig().passwordEncoder();

    private TemporaryPasswordAuthenticationProvider provider;

    @BeforeEach
    void setUp() {
        provider = new TemporaryPasswordAuthenticationProvider(
                userDetailsService, temporaryPasswordRepository, passwordEncoder
        );
    }

    @Test
    @DisplayName("사용 가능한 임시 비밀번호와 일치하면 인증한다.")
    void 사용_가능한_임시_비밀번호로_인증한다() {
        givenUser(false);
        givenTemporaryPasswords(issued(TEMPORARY_PASSWORD, LocalDateTime.now().plusMinutes(3)));

        Authentication result = provider.authenticate(request(TEMPORARY_PASSWORD));

        assertThat(result.isAuthenticated()).isTrue();
        assertThat(result.getPrincipal()).isInstanceOf(SecurityUser.class);
        assertThat(((SecurityUser) result.getPrincipal()).getUserId()).isEqualTo(USER_ID);
        assertThat(result.getAuthorities()).extracting(GrantedAuthority::getAuthority)
                .containsExactly("ROLE_USER");
    }

    @Test
    @DisplayName("만료된 임시 비밀번호는 거부한다.")
    void 만료된_임시_비밀번호는_거부한다() {
        givenUser(false);
        givenTemporaryPasswords(issued(TEMPORARY_PASSWORD, LocalDateTime.now().minusSeconds(1)));

        assertThatThrownBy(() -> provider.authenticate(request(TEMPORARY_PASSWORD)))
                .isInstanceOf(BadCredentialsException.class);
    }

    @Test
    @DisplayName("임시 비밀번호가 다르면 거부한다.")
    void 임시_비밀번호가_다르면_거부한다() {
        givenUser(false);
        givenTemporaryPasswords(issued(TEMPORARY_PASSWORD, LocalDateTime.now().plusMinutes(3)));

        assertThatThrownBy(() -> provider.authenticate(request("Wrong1!pass#")))
                .isInstanceOf(BadCredentialsException.class);
    }

    @Test
    @DisplayName("사용 가능한 임시 비밀번호가 없으면 거부한다.")
    void 사용_가능한_임시_비밀번호가_없으면_거부한다() {
        givenUser(false);
        givenTemporaryPasswords();

        assertThatThrownBy(() -> provider.authenticate(request(TEMPORARY_PASSWORD)))
                .isInstanceOf(BadCredentialsException.class);
    }

    @Test
    @DisplayName("가입하지 않은 이메일도 같은 인증 실패로 응답한다.")
    void 가입하지_않은_이메일도_같은_인증_실패로_응답한다() {
        given(userDetailsService.loadUserByUsername(EMAIL))
                .willThrow(new UsernameNotFoundException("not found"));

        // 가입 여부가 드러나지 않도록 UsernameNotFoundException을 그대로 내보내지 않는다.
        assertThatThrownBy(() -> provider.authenticate(request(TEMPORARY_PASSWORD)))
                .isExactlyInstanceOf(BadCredentialsException.class);
    }

    @Test
    @DisplayName("잠긴 계정은 임시 비밀번호가 맞아도 거부한다.")
    void 잠긴_계정은_임시_비밀번호가_맞아도_거부한다() {
        givenUser(true);
        givenTemporaryPasswords(issued(TEMPORARY_PASSWORD, LocalDateTime.now().plusMinutes(3)));

        // ProviderManager에서는 앞 Provider가 먼저 막지만, 이 Provider 단독으로도 잠금을 지킨다.
        assertThatThrownBy(() -> provider.authenticate(request(TEMPORARY_PASSWORD)))
                .isInstanceOf(LockedException.class);
    }

    @Test
    @DisplayName("이메일·비밀번호 로그인 요청을 처리한다.")
    void 이메일_비밀번호_로그인_요청을_처리한다() {
        assertThat(provider.supports(UsernamePasswordAuthenticationToken.class)).isTrue();
    }

    private UsernamePasswordAuthenticationToken request(String password) {
        return UsernamePasswordAuthenticationToken.unauthenticated(EMAIL, password);
    }

    private void givenUser(boolean locked) {
        given(userDetailsService.loadUserByUsername(EMAIL))
                .willReturn(new SecurityUser(USER_ID, EMAIL, "encoded-original", Role.USER, locked));
    }

    private void givenTemporaryPasswords(TemporaryPassword... temporaryPasswords) {
        given(temporaryPasswordRepository.findAllByUserIdAndStatus(USER_ID, TemporaryPasswordStatus.ACTIVE))
                .willReturn(List.of(temporaryPasswords));
    }

    private TemporaryPassword issued(String rawPassword, LocalDateTime expiredAt) {
        return TemporaryPassword.issue(USER_ID, passwordEncoder.encode(rawPassword), expiredAt);
    }
}
