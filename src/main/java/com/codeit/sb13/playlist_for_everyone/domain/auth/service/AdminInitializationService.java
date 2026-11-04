package com.codeit.sb13.playlist_for_everyone.domain.auth.service;


import com.codeit.sb13.playlist_for_everyone.domain.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class AdminInitializationService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    /**
     * 설정으로 전달받은 이메일을 기준으로 관리자 계정을 초기화한다.
     * 기존 계정이 있으면 권한, 비밀번호, 잠금 상태를 변경하지 않는다.
     */
    public void initialize(String email, String name, String password) {
        // RED 단계
        throw new UnsupportedOperationException("관리자 초기화 미구현");
    }

}
