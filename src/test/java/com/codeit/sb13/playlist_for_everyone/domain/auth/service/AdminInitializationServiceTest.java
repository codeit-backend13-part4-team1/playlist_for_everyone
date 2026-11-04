package com.codeit.sb13.playlist_for_everyone.domain.auth.service;

import com.codeit.sb13.playlist_for_everyone.domain.user.entity.Role;
import com.codeit.sb13.playlist_for_everyone.domain.user.entity.User;
import com.codeit.sb13.playlist_for_everyone.domain.user.repository.UserRepository;
import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.sql.SQLException;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
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

    @Test
    @DisplayName("관리자 저장 중 이메일 중복이 발생하면 예외 없이 초기화를 마친다")
    void 저장_중_이메일이_중복되면_건너뛴다() {
        // 조회 이후 다른 서버나 회원가입 요청이 같은 이메일을 저장할 수 있다.
        given(userRepository.findByEmail(EMAIL))
                .willReturn(Optional.empty());
        given(passwordEncoder.encode(PASSWORD))
                .willReturn(ENCODED_PASSWORD);

        ConstraintViolationException constraintException =
                new ConstraintViolationException(
                        "이메일 유니크 제약 위반",
                        new SQLException("duplicate key", "23505"),
                        "UQ_USERS_EMAIL"
                );

        DataIntegrityViolationException dbException =
                new DataIntegrityViolationException(
                        "관리자 저장 실패",
                        constraintException
                );

        given(userRepository.saveAndFlush(any(User.class)))
                .willThrow(dbException);

        // 예상 가능한 이메일 충돌 때문에 서버 시작이 실패하지 않아야 한다.
        assertThatCode(() ->
                adminInitializationService.initialize(EMAIL, NAME, PASSWORD))
                .doesNotThrowAnyException();

        // 충돌 이후 다시 저장하거나 기존 계정을 변경하지 않는다.
        verify(userRepository).findByEmail(EMAIL);
        verify(userRepository).saveAndFlush(any(User.class));
        verifyNoMoreInteractions(userRepository);
    }

    @Test
    @DisplayName("관리자 저장 중 이메일 중복이 아닌 무결성 오류는 그대로 전달한다")
    void 이메일_중복이_아닌_무결성_오류는_그대로_전달한다() {
        given(userRepository.findByEmail(EMAIL))
                .willReturn(Optional.empty());
        given(passwordEncoder.encode(PASSWORD))
                .willReturn(ENCODED_PASSWORD);

        ConstraintViolationException constraintException =
                new ConstraintViolationException(
                        "다른 제약 위반",
                        new SQLException("constraint violation"),
                        "OTHER_CONSTRAINT"
                );

        DataIntegrityViolationException dbException =
                new DataIntegrityViolationException(
                        "관리자 저장 실패",
                        constraintException
                );

        given(userRepository.saveAndFlush(any(User.class)))
                .willThrow(dbException);

        // 모든 무결성 오류를 이메일 중복으로 취급하면 실제 문제를 숨기게 된다.
        assertThatThrownBy(() ->
                adminInitializationService.initialize(EMAIL, NAME, PASSWORD))
                .isSameAs(dbException);

        verify(userRepository).findByEmail(EMAIL);
        verify(userRepository).saveAndFlush(any(User.class));
        verifyNoMoreInteractions(userRepository);
    }





}