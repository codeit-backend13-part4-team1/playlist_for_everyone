package com.codeit.sb13.playlist_for_everyone.domain.auth.service;

import com.codeit.sb13.playlist_for_everyone.domain.auth.entity.TemporaryPassword;
import com.codeit.sb13.playlist_for_everyone.domain.auth.entity.TemporaryPasswordStatus;
import com.codeit.sb13.playlist_for_everyone.domain.auth.mail.PasswordResetMailSender;
import com.codeit.sb13.playlist_for_everyone.domain.auth.repository.TemporaryPasswordRepository;
import com.codeit.sb13.playlist_for_everyone.domain.user.entity.User;
import com.codeit.sb13.playlist_for_everyone.domain.user.repository.UserRepository;
import com.codeit.sb13.playlist_for_everyone.global.exception.user.UserNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;

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

    // 같은 계정의 재요청 간격
    static final Duration RESEND_INTERVAL = Duration.ofMinutes(1);

    //  시간당 최대 발급 횟수
    static final int MAX_ISSUES_PER_HOUR = 5;
    static final Duration ISSUE_WINDOW = Duration.ofHours(1);

    private final UserRepository userRepository;
    private final TemporaryPasswordRepository  temporaryPasswordRepository;
    private final TemporaryPasswordGenerator temporaryPasswordGenerator;
    private final PasswordEncoder passwordEncoder;
    private final PasswordResetMailSender passwordResetMailSender;

    @Transactional
    public void resetPassword(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(()-> new UserNotFoundException(Map.of("email", email)));

        // 사용자당 사용 가능한 임시 비밀번호는 하나만 둔다.
        temporaryPasswordRepository
                .findAllByUserIdAndStatus(user.getId(), TemporaryPasswordStatus.ACTIVE)
                .forEach(TemporaryPassword::discard);

        String rawPassword = temporaryPasswordGenerator.generate();
        LocalDateTime expiredAt = LocalDateTime.now().plus(TEMPORARY_PASSWORD_VALIDITY);

        // DB에는 해시만 저장하고, 원문은 메일로만 전달한다.
        temporaryPasswordRepository.save(
                TemporaryPassword.issue(user.getId(), passwordEncoder.encode(rawPassword), expiredAt)
        );

        // 마지막에 보낸다. 실패하면 예외로 위의 파기·저장이 모두 롤백된다.
        passwordResetMailSender.send(user.getEmail(), rawPassword, TEMPORARY_PASSWORD_VALIDITY);
    }

}
