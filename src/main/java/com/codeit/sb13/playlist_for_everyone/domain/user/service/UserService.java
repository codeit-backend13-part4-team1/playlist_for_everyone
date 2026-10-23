package com.codeit.sb13.playlist_for_everyone.domain.user.service;

import com.codeit.sb13.playlist_for_everyone.domain.user.dto.command.UserCreateCommand;
import com.codeit.sb13.playlist_for_everyone.domain.user.dto.response.UserDto;
import com.codeit.sb13.playlist_for_everyone.domain.user.entity.Role;
import com.codeit.sb13.playlist_for_everyone.domain.user.entity.User;
import com.codeit.sb13.playlist_for_everyone.domain.user.repository.UserRepository;
import com.codeit.sb13.playlist_for_everyone.global.exception.user.DuplicateEmailException;
import lombok.RequiredArgsConstructor;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

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
                .role(Role.USER)
                .build();

        User savedUser;

        try {
            savedUser = userRepository.saveAndFlush(newUser);
        } catch (DataIntegrityViolationException exception) {
            if (isEmailUniqueViolation(exception)) {
                throw new DuplicateEmailException(command.email(), exception);
            }
            throw exception;
        }

        return new UserDto(
                savedUser.getId(),
                savedUser.getCreatedAt(),
                savedUser.getEmail(),
                savedUser.getName(),
                null,
                savedUser.getRole(),
                savedUser.isLocked()

        );
    }

    private boolean isEmailUniqueViolation(Throwable exception) {
        Throwable cause = exception;

        while (cause != null) {
            if (cause instanceof ConstraintViolationException violation && "UQ_USERS_EMAIL".equalsIgnoreCase(
                    violation.getConstraintName())) {
                return true;
            }

            cause = cause.getCause();
        }
        return false;
    }




}
