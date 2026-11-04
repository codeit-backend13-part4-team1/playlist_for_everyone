package com.codeit.sb13.playlist_for_everyone.domain.auth.service;


import com.codeit.sb13.playlist_for_everyone.domain.user.entity.Role;
import com.codeit.sb13.playlist_for_everyone.domain.user.entity.User;
import com.codeit.sb13.playlist_for_everyone.domain.user.repository.UserConstraintViolationDetector;
import com.codeit.sb13.playlist_for_everyone.domain.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class AdminInitializationService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    /**
     * 환경변수로 전달받은 이메일을 기준으로 관리자 계정을 초기화한다.
     * 기존 계정이 있으면 권한, 비밀번호, 잠금 상태를 변경하지 않는다.
     */
    public void initialize(String email, String name, String password) {
        Optional<User> exitingUser = userRepository.findByEmail(email);

        if (exitingUser.isPresent()) {
            User user = exitingUser.get();

            if (user.getRole() == Role.ADMIN) {
                log.info("[AUTH] 초기화 대상 관리자 계정이 이미 존재하여 건너뜁니다");
            } else {
                log.warn("[AUTH] 관리자 초기화 이메일이 일반 사용자 계정과 충돌하여 건너뜁니다");
            }
            return;
        }

        // 새 계정을 생성하는 경우에만 비밀번호를 인코딩한다.
        String encodedPassword = passwordEncoder.encode(password);

        User admin = User.builder()
                .email(email)
                .name(name)
                .password(encodedPassword)
                .role(Role.ADMIN)
                .build();

        try {
            // INSERT를 즉시 실행해 DB 제약 조건 위반도 이 시점에 확인 한다.
            userRepository.saveAndFlush(admin);
        } catch (DataIntegrityViolationException exception) {
            if (UserConstraintViolationDetector.isEmailUniqueConstraintViolation(exception)) {
                log.warn("[AUTH] 동일 이메일 계정이 생성되어 관리자 초기화를 건너뜁니다.");
                return;
            }
            // 이메일 중복 이외의 오류는 숨기지 않고 시작 실패의 원인으로 전달한다.
            throw exception;
        }

        log.info("[AUTH] 초기 관리자 계정을 생성했습니다.");
    }



}
