package com.codeit.sb13.playlist_for_everyone.domain.user.service;

import com.codeit.sb13.playlist_for_everyone.domain.user.dto.command.UserCreateCommand;
import com.codeit.sb13.playlist_for_everyone.domain.user.dto.response.UserDto;
import com.codeit.sb13.playlist_for_everyone.domain.user.entity.Role;
import com.codeit.sb13.playlist_for_everyone.domain.user.entity.User;
import com.codeit.sb13.playlist_for_everyone.domain.user.repository.UserRepository;
import com.codeit.sb13.playlist_for_everyone.global.exception.ApiErrorCode;
import com.codeit.sb13.playlist_for_everyone.global.exception.user.DuplicateEmailException;
import com.codeit.sb13.playlist_for_everyone.global.exception.user.UserNotFoundException;
import com.codeit.sb13.playlist_for_everyone.global.file.entity.FileInfo;
import com.codeit.sb13.playlist_for_everyone.global.file.service.FileService;
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
import org.springframework.test.util.ReflectionTestUtils;

import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.BDDMockito.*;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private FileService fileService;

    @InjectMocks
    private UserService userService;


    @Test
    @DisplayName("비밀번호를 암호화하고 일반 사용자로 가입한다")
    void 비밀번호를_암호화하고_일반_사용자로_가입한다() {

        UserCreateCommand command = new UserCreateCommand(
                "test@gmail.com",
                "경석",
                "password",
                Role.USER
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

        verifyNoInteractions(fileService);
    }

    @Test
    @DisplayName("이미 가입된 이메일이면 예외를 발생시키고 저장하지 않는다.")
    void 이미_가입된_이메일이면_예외를_발생시키고_저장하지_않는다() {

        UserCreateCommand command = new UserCreateCommand(
                "test@gmail.com",
                "테스트",
                "password",
                Role.USER
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
                "password",
                Role.USER
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

        assertThatThrownBy(() -> userService.create(command))
                .isInstanceOf(DuplicateEmailException.class)
                .hasCause(dbException);
    }

    @Test
    @DisplayName("이메일 중복이 아닌 데이터 무결서 오류는 그대로 전달한다.")
    void 다른_데이터_무결성_오류는_그대로_전달한다() {

        UserCreateCommand command = new UserCreateCommand(
                "test@gmail.com",
                "테스트",
                "password",
                Role.USER
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

        assertThatThrownBy(() -> userService.create(command))
                .isSameAs(dbException);

    }


    @Test
    @DisplayName("사용자가 존재하면 조회한 사용자를 반환한다")
    void 사용자가_존재하면_반환한다() {
        UUID userId = UUID.randomUUID();

        User user = User.builder()
                .email("test@gmai.com")
                .name("테스트")
                .password("encoded-password")
                .role(Role.USER)
                .build();

        ReflectionTestUtils.setField(user, "id", userId);

        given(userRepository.findWithProfileImageById(userId))
                .willReturn(Optional.of(user));

        User result = userService.getById(userId);

        assertThat(result).isSameAs(user);
        assertThat(result.getId()).isEqualTo(userId);

        verify(userRepository).findWithProfileImageById(userId);
    }

    @Test
    @DisplayName("사용자가 없으면 사용자ID를 포함한 공통 예외를 발생시킨다")
    void 사용자가_없으면_예외가_발생한다() {
        UUID userId = UUID.randomUUID();

        given(userRepository.findWithProfileImageById(userId))
                .willReturn(Optional.empty());

        assertThatThrownBy(() -> userService.getById(userId))
                .isInstanceOfSatisfying(
                        UserNotFoundException.class,
                        exception -> {
                            assertThat(exception.getApiErrorCode())
                                    .isEqualTo(ApiErrorCode.USER_NOT_FOUND);
                            assertThat(exception.getDetails())
                                    .containsEntry("userId", userId);
                        }
                );

        verify(userRepository).findWithProfileImageById(userId);
    }

    @Test
    @DisplayName("프로필 이미지가 없으면 URL 없이 사용자 정보를 반환한다")
    void 프로필_이미지가_없으면_URL없이_반환한다() {
        UUID userId = UUID.randomUUID();
        User user = createProfileTestUser(userId);

        given(userRepository.findWithProfileImageById(userId))
                .willReturn(Optional.of(user));

        UserDto result = userService.getUserDtoById(userId);

        // 기존 응답 필드가 유지되는지도 함께 확인한다.
        assertThat(result.id()).isEqualTo(userId);
        assertThat(result.createdAt()).isEqualTo(user.getCreatedAt());
        assertThat(result.email()).isEqualTo(user.getEmail());
        assertThat(result.name()).isEqualTo(user.getName());
        assertThat(result.role()).isEqualTo(Role.USER);
        assertThat(result.locked()).isFalse();
        assertThat(result.profileImageUrl()).isNull();

        verify(userRepository).findWithProfileImageById(userId);

        // 이미지가 없으면 불필요한 파일 조회나 URL 생성을 하지 않는다.
        verifyNoInteractions(fileService);
    }

    @Test
    @DisplayName("프로필 이미지가 있으면 파일 서비스의 URL을 사용자 응답에 반영한다")
    void 프로필_이미지가_있으면_URL을_반환한다() {
        UUID userId = UUID.randomUUID();
        UUID fileId = UUID.randomUUID();

        User user = createProfileTestUser(userId);
        FileInfo image = createProfileTestImage(fileId);
        user.updateProfileImage(image);

        // 권한과 잠금 상태도 변환 과정에서 그대로 유지되어야 한다.
        user.updateRole(Role.ADMIN);
        user.lock();

        String expectedUrl =
                "https://example.com/profile.png?signature=test";

        given(userRepository.findWithProfileImageById(userId))
                .willReturn(Optional.of(user));
        given(fileService.getFileUrl(fileId))
                .willReturn(expectedUrl);

        UserDto result = userService.getUserDtoById(userId);

        assertThat(result.id()).isEqualTo(userId);
        assertThat(result.createdAt()).isEqualTo(user.getCreatedAt());
        assertThat(result.email()).isEqualTo(user.getEmail());
        assertThat(result.name()).isEqualTo(user.getName());
        assertThat(result.role()).isEqualTo(Role.ADMIN);
        assertThat(result.locked()).isTrue();
        assertThat(result.profileImageUrl()).isEqualTo(expectedUrl);

        // 사용자 ID가 아닌 연결된 파일 ID로 URL을 요청해야 한다.
        verify(userRepository).findWithProfileImageById(userId);
        verify(fileService).getFileUrl(fileId);
    }

    @Test
    @DisplayName("사용자 응답 조회 시 사용자가 없으면 공통 예외를 발생시킨다")
    void 응답_조회_시_사용자가_없으면_예외가_발생한다() {
        UUID userId = UUID.randomUUID();

        given(userRepository.findWithProfileImageById(userId))
                .willReturn(Optional.empty());

        assertThatThrownBy(() -> userService.getUserDtoById(userId))
                .isInstanceOfSatisfying(
                        UserNotFoundException.class,
                        exception -> {
                            assertThat(exception.getApiErrorCode())
                                    .isEqualTo(ApiErrorCode.USER_NOT_FOUND);
                            assertThat(exception.getDetails())
                                    .containsEntry("userId", userId);
                        }
                );

        verify(userRepository).findWithProfileImageById(userId);
        verifyNoInteractions(fileService);
    }

    @Test
    @DisplayName("프로필 URL 생성에 실패하면 예외를 그대로 전달한다")
    void 프로필_URL_생성_실패를_그대로_전달한다() {
        UUID userId = UUID.randomUUID();
        UUID fileId = UUID.randomUUID();

        User user = createProfileTestUser(userId);
        user.updateProfileImage(createProfileTestImage(fileId));

        RuntimeException failure =
                new IllegalStateException("파일 URL 생성 실패");

        given(userRepository.findWithProfileImageById(userId))
                .willReturn(Optional.of(user));
        given(fileService.getFileUrl(fileId))
                .willThrow(failure);

        // 이미지가 없는 정상 상태와 URL 생성 실패를 구분한다.
        // 실패를 null로 바꾸어 숨기지 않고 원래 예외를 전달한다.
        assertThatThrownBy(() -> userService.getUserDtoById(userId))
                .isSameAs(failure);

        verify(fileService).getFileUrl(fileId);
    }

    /**
     * DB 없이 저장된 사용자 상태를 준비한다.
     * ID와 생성 시각은 실제로는 영속화 과정에서 설정되는 값이다.
     */
    private User createProfileTestUser(UUID userId) {
        User user = User.builder()
                .email("profile@example.com")
                .name("프로필 사용자")
                .password("encoded-password")
                .role(Role.USER)
                .build();

        ReflectionTestUtils.setField(user, "id", userId);
        ReflectionTestUtils.setField(
                user,
                "createdAt",
                LocalDateTime.of(2026, 11, 5, 12, 0)
        );

        return user;
    }

    /**
     * 저장된 파일 정보를 가정한다.
     * 실제 S3 업로드나 DB 저장은 수행하지 않는다.
     */
    private FileInfo createProfileTestImage(UUID fileId) {
        FileInfo image = FileInfo.createS3File(
                "profiles/test.png",
                "profile.png",
                "image/png",
                1024L
        );

        ReflectionTestUtils.setField(image, "id", fileId);

        return image;
    }
}
