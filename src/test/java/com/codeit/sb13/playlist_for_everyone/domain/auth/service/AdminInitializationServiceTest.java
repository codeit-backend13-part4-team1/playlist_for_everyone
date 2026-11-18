package com.codeit.sb13.playlist_for_everyone.domain.auth.service;

import com.codeit.sb13.playlist_for_everyone.domain.user.dto.command.UserCreateCommand;
import com.codeit.sb13.playlist_for_everyone.domain.user.entity.Role;
import com.codeit.sb13.playlist_for_everyone.domain.user.entity.User;
import com.codeit.sb13.playlist_for_everyone.domain.user.repository.UserRepository;
import com.codeit.sb13.playlist_for_everyone.domain.user.service.UserService;
import com.codeit.sb13.playlist_for_everyone.global.exception.user.DuplicateEmailException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

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

    @Mock
    private UserRepository userRepository;
    @Mock
    private UserService userService;

    @InjectMocks
    private AdminInitializationService adminInitializationService;

    @Test
    @DisplayName("계정이 없으면 ADMIN 권한으로 공통 계정 생성 서비스를 호출한다")
    void 계정이_없으면_ADMIN_생성을_위임한다() {
        given(userRepository.findByEmail(EMAIL))
                .willReturn(Optional.empty());

        adminInitializationService.initialize(EMAIL, NAME, PASSWORD);

        // 비밀번호 원문과 ADMIN 권한을 공통 서비스에 전달한다.
        // 실제 비밀번호 해시와 저장은 UserService의 책임이다.
        UserCreateCommand expectedCommand =
                new UserCreateCommand(EMAIL, NAME, PASSWORD, Role.ADMIN);

        verify(userRepository).findByEmail(EMAIL);
        verify(userService).create(expectedCommand);

        // 초기화 서비스가 직접 저장하거나 생성 요청을 반복하지 않는지 확인한다.
        verifyNoMoreInteractions(userRepository, userService);
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

        // 기존 계정이 잠겨 있어도 초기화 과정에서 해제하지 않는다.
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
        verifyNoMoreInteractions(userRepository);

        // 기존 USER 계정을 발견하면 생성 서비스를 호출하지 않는다.
        verifyNoInteractions(userService);
    }

    @Test
    @DisplayName("동일 이메일의 ADMIN 계정이 있으면 기존 정보와 잠금 상태를 유지한다")
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

        // 새 설정값으로 기존 관리자 정보를 덮어쓰지 않는다.
        assertThat(existingAdmin.getEmail()).isEqualTo(EMAIL);
        assertThat(existingAdmin.getName()).isEqualTo("기존 관리자");
        assertThat(existingAdmin.getPassword())
                .isEqualTo("existing-admin-password");
        assertThat(existingAdmin.getRole()).isEqualTo(Role.ADMIN);
        assertThat(existingAdmin.isLocked()).isTrue();

        verify(userRepository).findByEmail(EMAIL);
        verifyNoMoreInteractions(userRepository);
        verifyNoInteractions(userService);
    }

    @Test
    @DisplayName("계정 생성 중 이메일이 중복되면 예외 없이 초기화를 마친다")
    void 생성_중_이메일이_중복되면_건너뛴다() {
        given(userRepository.findByEmail(EMAIL))
                .willReturn(Optional.empty());

        UserCreateCommand command =
                new UserCreateCommand(EMAIL, NAME, PASSWORD, Role.ADMIN);

        // 사전 조회 이후 다른 서버나 회원가입 요청이 먼저 계정을 만든 상황이다.
        // 공통 서비스는 이메일 충돌을 DuplicateEmailException으로 전달한다.
        given(userService.create(command))
                .willThrow(new DuplicateEmailException(EMAIL));

        assertThatCode(() ->
                adminInitializationService.initialize(EMAIL, NAME, PASSWORD))
                .doesNotThrowAnyException();

        verify(userRepository).findByEmail(EMAIL);
        verify(userService).create(command);

        // 충돌 이후 재생성하거나 기존 계정을 변경하려고 시도하지 않는다.
        verifyNoMoreInteractions(userRepository, userService);
    }

    @Test
    @DisplayName("이메일 중복이 아닌 무결성 오류는 그대로 전달한다")
    void 이메일_중복이_아닌_무결성_오류는_그대로_전달한다() {
        given(userRepository.findByEmail(EMAIL))
                .willReturn(Optional.empty());

        UserCreateCommand command =
                new UserCreateCommand(EMAIL, NAME, PASSWORD, Role.ADMIN);

        DataIntegrityViolationException exception =
                new DataIntegrityViolationException("다른 제약 조건 위반");

        given(userService.create(command))
                .willThrow(exception);

        // 이메일 중복 이외의 저장 실패를 정상 초기화로 숨기지 않는다.
        assertThatThrownBy(() ->
                adminInitializationService.initialize(EMAIL, NAME, PASSWORD))
                .isSameAs(exception);

        verify(userRepository).findByEmail(EMAIL);
        verify(userService).create(command);
        verifyNoMoreInteractions(userRepository, userService);
    }
}