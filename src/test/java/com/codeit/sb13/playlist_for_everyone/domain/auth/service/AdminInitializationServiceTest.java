package com.codeit.sb13.playlist_for_everyone.domain.auth.service;

import com.codeit.sb13.playlist_for_everyone.domain.user.entity.Role;
import com.codeit.sb13.playlist_for_everyone.domain.user.entity.User;
import com.codeit.sb13.playlist_for_everyone.domain.user.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AdminInitializationServiceTest {

    // 실제 관리자 정보가 아닌 테스트 전용 값
    private static final String EMAIL = "admin@example.com";
    private static final String NAME = "초기 관리자";
    private static final String PASSWORD = "AdminPassword123!";
    private static final String ENCODED_PASSWORD = "encoded-admin-password";

    @Mock
    private UserRepository userRepository;
    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private AdminInitializationService adminInitializationService;

    @Test
    @DisplayName("저장한 이메일의 계정이 없으면 ADMIN 계정을 생성한다.")
    void 계정이_없으면_관리자를_생성한다() {
        given(userRepository.findByEmail(EMAIL)).willReturn(Optional.empty());

        given(passwordEncoder.encode(PASSWORD)).willReturn(ENCODED_PASSWORD);

        adminInitializationService.initialize(EMAIL, NAME, PASSWORD);

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).saveAndFlush(captor.capture());

        User savedUser = captor.getValue();

        assertThat(savedUser.getEmail()).isEqualTo(EMAIL);
        assertThat(savedUser.getName()).isEqualTo(NAME);
        assertThat(savedUser.getRole()).isEqualTo(Role.ADMIN);

        // 입력한 비밀번호 원문이 아닌 인코더의 반환값을 저장해야 한다.
        assertThat(savedUser.getPassword()).isEqualTo(ENCODED_PASSWORD);
        assertThat(savedUser.getPassword()).isNotEqualTo(PASSWORD);

        // 새로 생성한 관리자는 잠기지 않은 상태여야 한다.
        assertThat(savedUser.isEnabled()).isTrue();
        assertThat(savedUser.isLocked()).isFalse();

        verify(userRepository).findByEmail(EMAIL);
        verify(passwordEncoder).encode(PASSWORD);
    }

    @Test
    @DisplayName("동일 이메일의 USER 계정이 있으면 승격하거나 정보를 변경하지 않는다")
    void 기존_USER_계정은_변경하지_않는다() {
        User existingUser = User.builder()
                .email(EMAIL)
                .name("기존 사용자")
                .password("existing-user-password")
                .role(Role.USER)
                .build();

        // 초기화 과정에서 기존 계정의 잠금까지 풀리지 않는지 확인한다.
        existingUser.lock();

        given(userRepository.findByEmail(EMAIL))
                .willReturn(Optional.of(existingUser));

        adminInitializationService.initialize(EMAIL, NAME, PASSWORD);

        assertThat(existingUser.getEmail()).isEqualTo(EMAIL);
        assertThat(existingUser.getName()).isEqualTo("기존 사용자");
        assertThat(existingUser.getPassword())
                .isEqualTo("existing-user-password");
        assertThat(existingUser.getRole()).isEqualTo(Role.USER);
        assertThat(existingUser.isLocked()).isTrue();

        verify(userRepository).findByEmail(EMAIL);
        verify(userRepository, never()).saveAndFlush(any(User.class));
        verifyNoInteractions(passwordEncoder);
    }

    @Test
    @DisplayName("동일 이메일의 ADMIN 계정이 있으면 기존 정보를 유지한다")
    void 기존_ADMIN_계정은_변경하지_않는다() {
        User existingAdmin = User.builder()
                .email(EMAIL)
                .name("기존 관리자")
                .password("existing-admin-password")
                .role(Role.ADMIN)
                .build();

        existingAdmin.lock();

        given(userRepository.findByEmail(EMAIL))
                .willReturn(Optional.of(existingAdmin));

        adminInitializationService.initialize(EMAIL, NAME, PASSWORD);

        assertThat(existingAdmin.getEmail()).isEqualTo(EMAIL);
        assertThat(existingAdmin.getName()).isEqualTo("기존 관리자");
        assertThat(existingAdmin.getPassword())
                .isEqualTo("existing-admin-password");
        assertThat(existingAdmin.getRole()).isEqualTo(Role.ADMIN);
        assertThat(existingAdmin.isLocked()).isTrue();

        verify(userRepository).findByEmail(EMAIL);
        verify(userRepository, never()).saveAndFlush(any(User.class));
        verifyNoInteractions(passwordEncoder);
    }





}