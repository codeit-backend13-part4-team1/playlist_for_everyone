package com.codeit.sb13.playlist_for_everyone.domain.user.service;

import com.codeit.sb13.playlist_for_everyone.domain.user.dto.command.UserCreateCommand;
import com.codeit.sb13.playlist_for_everyone.domain.user.dto.response.UserDto;
import com.codeit.sb13.playlist_for_everyone.domain.user.entity.User;
import com.codeit.sb13.playlist_for_everyone.domain.user.repository.UserConstraintViolationDetector;
import com.codeit.sb13.playlist_for_everyone.domain.user.repository.UserRepository;
import com.codeit.sb13.playlist_for_everyone.global.exception.user.DuplicateEmailException;
import com.codeit.sb13.playlist_for_everyone.global.exception.user.UserNotFoundException;
import com.codeit.sb13.playlist_for_everyone.global.file.entity.FileInfo;
import com.codeit.sb13.playlist_for_everyone.global.file.service.FileService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final FileService fileService;

    @Transactional
    public UserDto create(UserCreateCommand command) {
        // 이메일 중복 검사
        if (userRepository.existsByEmail(command.email())) {
            throw new DuplicateEmailException(command.email());
        }

        String encodedPassword = passwordEncoder.encode(command.password());

        User newUser = User.builder()
                .email(command.email())
                .name(command.name())
                .password(encodedPassword)
                // 내부 호출자가 회원가입 또는 관리자 초기화에 맞는 권한을 지정한다.
                .role(command.role())
                .build();

        User savedUser;

        try {
            savedUser = userRepository.saveAndFlush(newUser);
        } catch (DataIntegrityViolationException exception) {
            if (UserConstraintViolationDetector.isEmailUniqueConstraintViolation(exception)) {
                throw new DuplicateEmailException(command.email(), exception);
            }
            // 이메일 중복이 아닌 오류는 원인을 숨기지 않고 그대로 전달한다.
            throw exception;
        }

        log.info("[USER] 생성 완료 userId - {}", savedUser.getId());

        return toUserDto(savedUser);
    }

    /**
     * 프로필 이미지 URL을 포함한 사용자 응답을 조회
     * 엔티티 조회와 응답 변환을 같은 트랜잭션 안에서 수행한다.
     */
    @Transactional(readOnly = true)
    public UserDto getUserDtoById(UUID userId) {
        User user = getById(userId);

        return toUserDto(user);
    }

    private UserDto toUserDto(User user) {
        FileInfo profileImage = user.getProfileImage();

        // 이미지 없는 사용자는 기존과 동일하게 null을 반환한다.
        String profileImageUrl = profileImage == null
                ? null
                : fileService.getFileUrl(profileImage.getId());

        return new UserDto(
                user.getId(),
                user.getCreatedAt(),
                user.getEmail(),
                user.getName(),
                profileImageUrl,
                user.getRole(),
                user.isLocked()
        );

    }



    /**
     * 사용자 ID로 조회하는 공통 메서드
     *
     * 사용자가 없으면 UserNotFoundException이 발생합니다
     */
    @Transactional(readOnly = true)
    public User getById(UUID ownerId) {
        User owner = userRepository.findWithProfileImageById(ownerId)
                .orElseThrow(() -> new UserNotFoundException(
                        Map.of("userId", ownerId)
                ));

        return owner;
    }





}
