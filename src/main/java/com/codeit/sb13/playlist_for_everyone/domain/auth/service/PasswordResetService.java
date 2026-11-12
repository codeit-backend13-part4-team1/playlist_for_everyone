package com.codeit.sb13.playlist_for_everyone.domain.auth.service;

import com.codeit.sb13.playlist_for_everyone.domain.auth.mail.PasswordResetMailSender;
import com.codeit.sb13.playlist_for_everyone.domain.auth.repository.TemporaryPasswordRepository;
import com.codeit.sb13.playlist_for_everyone.domain.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;

/**
 * 비밀번호 초기화: 임시 비밀번호를 발급하고 이메일로 보낸다.
 *
 * 사용자의 기존 비밀번호를 바꾸지 않는다. 임시 비밀번호는 별도로 저장하고, 만료 전까지만 로그인에 사용할 수 있다.
 * 메일 발송이 실패하면 예외로 트랜잭션이 롤백되어 발급도 취소된다.
 */

@Service
@RequiredArgsConstructor
public class PasswordResetService {

    static final Duration TEMPORARY_PASSWORD_VALIDITY = Duration.ofMinutes(3);

    private final UserRepository userRepository;
    private final TemporaryPasswordRepository  temporaryPasswordRepository;
    private final TemporaryPasswordGenerator temporaryPasswordGenerator;
    private final PasswordEncoder passwordEncoder;
    private final PasswordResetMailSender passwordResetMailSender;

    @Transactional
    public void resetPassword(String email) {
        // RED: 다음 단계에서 구현
    }


}
