package com.codeit.sb13.playlist_for_everyone.domain.auth.security;

import com.codeit.sb13.playlist_for_everyone.domain.user.entity.Role;
import com.codeit.sb13.playlist_for_everyone.domain.user.entity.User;
import com.codeit.sb13.playlist_for_everyone.domain.user.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("인증 사용자 조회")
class CustomUserDetailsServiceTest {

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private CustomUserDetailsService userDetailsService;

    @Test
    @DisplayName("이메일로 사용자를 조회하고 인증 정보를 변환한다")
    void 이메일로_인증_정보를_조회한다() {
        UUID userId = UUID.randomUUID();

        User user = createUser();

        ReflectionTestUtils.setField(user,  "id", userId);

        given(userRepository.findByEmail("test@gmail.com"))
                .willReturn(Optional.of(user));

        SecurityUser result = userDetailsService.loadUserByUsername("test@gmail.com");

        assertThat(result.getUserId()).isEqualTo(userId);
        assertThat(result.getUsername()).isEqualTo("test@gmail.com");
        assertThat(result.getPassword()).isEqualTo("encoded-password");
        assertThat(result.getAuthorities())
                .extracting(GrantedAuthority::getAuthority)
                .containsExactly("ROLE_USER");
        assertThat(result.isEnabled()).isTrue();
        assertThat(result.isAccountNonLocked()).isTrue();

        verify(userRepository).findByEmail("test@gmail.com");
    }

    @Test
    @DisplayName("존재하지 않는 이메일이면 인증용 사용자 미존재 예외가 발생한다")
    void 존재하지_않는_이메일이면_예외가_발생한다() {
        given(userRepository.findByEmail("unknown@gmail.com"))
                .willReturn(Optional.empty());

        assertThatThrownBy(() ->
                userDetailsService.loadUserByUsername("unknown@gmail.com"))
                .isInstanceOf(UsernameNotFoundException.class);
    }

    @Test
    @DisplayName("잠김 사용자는 잠금 상태를 유지한 인증 정보를 변환한다")
    void 잠김_사용자의_상태를_유지한다() {
        User user = createUser();
        user.lock();

        given(userRepository.findByEmail("test@gmail.com"))
                .willReturn(Optional.of(user));

        SecurityUser result = userDetailsService.loadUserByUsername("test@gmail.com");

        assertThat(result.isEnabled()).isFalse();
        assertThat(result.isAccountNonLocked()).isFalse();
    }

    private User createUser() {
        return User.builder()
                .email("test@gmail.com")
                .name("테스트")
                .password("encoded-password")
                .role(Role.USER)
                .build();
    }



}
