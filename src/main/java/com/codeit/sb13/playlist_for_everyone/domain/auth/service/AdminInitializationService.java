package com.codeit.sb13.playlist_for_everyone.domain.auth.service;


import com.codeit.sb13.playlist_for_everyone.domain.user.dto.command.UserCreateCommand;
import com.codeit.sb13.playlist_for_everyone.domain.user.entity.Role;
import com.codeit.sb13.playlist_for_everyone.domain.user.entity.User;
import com.codeit.sb13.playlist_for_everyone.domain.user.repository.UserRepository;
import com.codeit.sb13.playlist_for_everyone.domain.user.service.UserService;
import com.codeit.sb13.playlist_for_everyone.global.exception.user.DuplicateEmailException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class AdminInitializationService {

    private final UserRepository userRepository;
    private final UserService userService;

    /**
     * 환경변수로 전달받은 이메일을 기준으로 관리자 계정을 초기화한다.
     * 기존 계정이 있으면 권한, 비밀번호, 잠금 상태를 변경하지 않는다.
     */
    public void initialize(String email, String name, String password) {
        Optional<User> existingUser = userRepository.findByEmail(email);

        if (existingUser.isPresent()) {
            User user = existingUser.get();

            if (user.getRole() == Role.ADMIN) {
                log.info("[AUTH] 초기화 대상 관리자 계정이 이미 존재하여 건너뜁니다");
            } else {
                log.warn("[AUTH] 관리자 초기화 이메일이 일반 사용자 계정과 충돌하여 건너뜁니다");
            }
            return;
        }

        UserCreateCommand command = new UserCreateCommand(email, name, password, Role.ADMIN);

        try {
            // 비밀번호 해시와 계정 저장은 공통 생성 서비스에 위임한다.
            userService.create(command);
        } catch (DuplicateEmailException exception) {
            log.warn("[AUTH] 동일 이메일 계정이 생성되어 관리자 초기화를 건너뜁니다.");
            return;
        }

        log.info("[AUTH] 초기 관리자 계정을 생성했습니다.");

    }


}




