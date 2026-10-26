package com.codeit.sb13.playlist_for_everyone.domain.user.service;

import com.codeit.sb13.playlist_for_everyone.domain.user.dto.command.UserCreateCommand;
import com.codeit.sb13.playlist_for_everyone.domain.user.dto.response.UserDto;
import com.codeit.sb13.playlist_for_everyone.domain.user.entity.Role;
import com.codeit.sb13.playlist_for_everyone.domain.user.entity.User;
import com.codeit.sb13.playlist_for_everyone.domain.user.repository.UserRepository;
import com.codeit.sb13.playlist_for_everyone.global.exception.user.DuplicateEmailException;
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

import static org.assertj.core.api.Assertions.*;
import static org.mockito.BDDMockito.*;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private UserService userService;


    @Test
    @DisplayName("비밀번호를 암호화하고 일반 사용자로 가입한다")
    void 비밀번호를_암호화하고_일반_사용자로_가입한다() {

        UserCreateCommand command = new UserCreateCommand(
                "test@gmail.com",
                "경석",
                "password"
        );

        given(userRepository.existsByEmail(command.email()))
                .willReturn(false);

        given(passwordEncoder.encode(command.password()))
                .willReturn("encoded-password");

        given(userRepository.saveAndFlush(any(User.class)))
                .willAnswer(invocation -> invocation.getArgument(0));

        UserDto response = userService.create(command);

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);

        verify(userRepository).saveAndFlush(userCaptor.capture());

        User savedUser = userCaptor.getValue();

        assertThat(savedUser.getEmail()).isEqualTo(command.email());
        assertThat(savedUser.getName()).isEqualTo(command.name());
        assertThat(savedUser.getPassword()).isEqualTo("encoded-password");
        assertThat(savedUser.getRole()).isEqualTo(Role.USER);
        assertThat(savedUser.isEnabled()).isTrue();
        assertThat(savedUser.isLocked()).isFalse();

        verify(passwordEncoder).encode(command.password());

        assertThat(response).isNotNull();
        assertThat(response.email()).isEqualTo(command.email());
        assertThat(response.name()).isEqualTo(command.name());
        assertThat(response.role()).isEqualTo(Role.USER);
        assertThat(response.locked()).isFalse();
        assertThat(response.profileImageUrl()).isNull();
    }

    @Test
    @DisplayName("이미 가입된 이메일이면 예외를 발생시키고 저장하지 않는다.")
    void 이미_가입된_이메일이면_예외를_발생시키고_저장하지_않는다() {

        UserCreateCommand command = new UserCreateCommand(
                "test@gmail.com",
                "테스트",
                "password"
        );

        given(userRepository.existsByEmail(command.email()))
                .willReturn(true);

        assertThatThrownBy(() -> userService.create(command))
                .isInstanceOf(DuplicateEmailException.class);

        verify(userRepository, never()).saveAndFlush(any(User.class));
        verifyNoInteractions(passwordEncoder);

    }

    @Test
    @DisplayName("중복 검사를 통과해도 DB에서 이메일 중복이 발생하면 중복 예외로 변환한다.")
    void DB의_이메일_중복을_중복_이메일_예외로_변환한다() {

        UserCreateCommand command = new UserCreateCommand(
                "test@gmail.com",
                "테스트",
                "password"
        );

        given(userRepository.existsByEmail(command.email()))
                .willReturn(false);

        given(passwordEncoder.encode(command.password()))
                .willReturn("encoded-password");

        ConstraintViolationException constraintException = new ConstraintViolationException(
                "이메일 유니크 제약 위반", new SQLException("duplicate key", "23505"),
                "UQ_USERS_EMAIL"
        );

        DataIntegrityViolationException dbException = new DataIntegrityViolationException(
                "사용자 저장 실패", constraintException
        );

        given(userRepository.saveAndFlush(any(User.class)))
                .willThrow(dbException);

        assertThatThrownBy(()-> userService.create(command))
                .isInstanceOf(DuplicateEmailException.class)
                .hasCause(dbException);
    }

    @Test
    @DisplayName("이메일 중복이 아닌 데이터 무결서 오류는 그대로 전달한다.")
    void 다른_데이터_무결성_오류는_그대로_전달한다() {

        UserCreateCommand command = new UserCreateCommand(
                "test@gmail.com",
                "테스트",
                "password"
        );

        given(userRepository.existsByEmail(command.email()))
                .willReturn(false);

        given(passwordEncoder.encode(command.password()))
                .willReturn("encoded-password");

        DataIntegrityViolationException dbException = new DataIntegrityViolationException(
                "사용자 저장 실패", new ConstraintViolationException(
                "다른 제약 위반", new SQLException("constraint violation"), "OTHER_CONSTRAINT")

        );

        given(userRepository.saveAndFlush(any(User.class)))
                .willThrow(dbException);

        assertThatThrownBy(()-> userService.create(command))
                .isSameAs(dbException);

    }

}