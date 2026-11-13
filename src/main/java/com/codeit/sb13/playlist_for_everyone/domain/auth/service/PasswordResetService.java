package com.codeit.sb13.playlist_for_everyone.domain.auth.service;

import com.codeit.sb13.playlist_for_everyone.domain.auth.entity.TemporaryPassword;
import com.codeit.sb13.playlist_for_everyone.domain.auth.entity.TemporaryPasswordStatus;
import com.codeit.sb13.playlist_for_everyone.domain.auth.mail.PasswordResetMailSender;
import com.codeit.sb13.playlist_for_everyone.domain.auth.repository.TemporaryPasswordRepository;
import com.codeit.sb13.playlist_for_everyone.domain.user.entity.User;
import com.codeit.sb13.playlist_for_everyone.domain.user.repository.UserRepository;
import com.codeit.sb13.playlist_for_everyone.global.exception.auth.PasswordResetTooManyRequestsException;
import com.codeit.sb13.playlist_for_everyone.global.exception.user.UserNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

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

        // 같은 계정의 요처을 한 줄로 세운다. 뒤 요청은 앞 요청이 커밋한 발급 기록을 보고 제한을 판단한다.
        userRepository.findByIdForUpdate(user.getId());

        LocalDateTime now = LocalDateTime.now();

        // 제한을 넘으면 기존 임시 비밀번호를 건드리지 않고 거부한다.
        checkIssueLimit(user.getId(), now);

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

    /**
     * 같은 계정의 발급 횟수를 제한한다.
     * 최근 1시간 발급 기록을 최대 5개 가져온다. 5건이면 시간당 제한, 1건 이상이면 가장 최근 발급으로 재요청 간격을 판단한다.
     * 두 제한에 모두 걸리면 더 늦게 풀리는 시각으로 안내한다.
     */
    private void checkIssueLimit(UUID userId, LocalDateTime now) {
        List<TemporaryPassword> recentIssues =
                temporaryPasswordRepository.findTop5ByUserIdAndCreatedAtAfterOrderByCreatedAtDesc(userId, now.minus(ISSUE_WINDOW));

        LocalDateTime nextAllowedAt = now;

        if (!recentIssues.isEmpty()) {
            LocalDateTime byInterval = recentIssues.get(0).getCreatedAt().plus(RESEND_INTERVAL);
            nextAllowedAt = later(nextAllowedAt, byInterval);
        }

        if (recentIssues.size() >= MAX_ISSUES_PER_HOUR) {
            LocalDateTime byWindow = recentIssues.get(MAX_ISSUES_PER_HOUR -1).getCreatedAt().plus(ISSUE_WINDOW);
            nextAllowedAt = later(nextAllowedAt, byWindow);
        }

        if (nextAllowedAt.isAfter(now)) {
            throw new PasswordResetTooManyRequestsException(secondsUntil(now, nextAllowedAt));
        }


    }

    // 남은 시간을 초 단위로 올림한다.
    private long secondsUntil(LocalDateTime now, LocalDateTime target) {
        long millis = Duration.between(now, target).toMillis();
        return (millis + 999) / 1000;
    }

    private LocalDateTime later(LocalDateTime a, LocalDateTime b) {
        return a.isAfter(b) ? a : b;
    }

}
