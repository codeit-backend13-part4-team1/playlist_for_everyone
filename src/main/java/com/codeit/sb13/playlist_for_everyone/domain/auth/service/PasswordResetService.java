package com.codeit.sb13.playlist_for_everyone.domain.auth.service;

import com.codeit.sb13.playlist_for_everyone.domain.auth.entity.TemporaryPassword;
import com.codeit.sb13.playlist_for_everyone.domain.auth.entity.TemporaryPasswordStatus;
import com.codeit.sb13.playlist_for_everyone.domain.auth.event.TemporaryPasswordIssuedEvent;
import com.codeit.sb13.playlist_for_everyone.domain.auth.repository.TemporaryPasswordRepository;
import com.codeit.sb13.playlist_for_everyone.domain.user.entity.User;
import com.codeit.sb13.playlist_for_everyone.domain.user.repository.UserRepository;
import com.codeit.sb13.playlist_for_everyone.global.exception.auth.PasswordResetNotAllowedException;
import com.codeit.sb13.playlist_for_everyone.global.exception.auth.PasswordResetTooManyRequestsException;
import com.codeit.sb13.playlist_for_everyone.global.exception.user.UserNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 비밀번호 초기화: 임시 비밀번호를 발급하고 안내 메일 발송을 요청한다.
 *
 * 사용자의 기존 비밀번호를 바꾸지 않는다. 임시 비밀번호는 별도로 저장하고, 만료 전까지만 로그인에 사용할 수 있다.
 * 메일은 발급이 커밋된 뒤 트랜잭션 밖에서 보내, SMTP 응답을 기다리는 동안 DB 커넥션과 행 락을 잡고 있지 않는다.
 * 비밀번호 없이 소셜 로그인으로 가입한 계정은 초기화 대상이 아니다.
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
    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    public void resetPassword(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(()-> new UserNotFoundException(Map.of("email", email)));

        // 소셜 로그인으로 가입해 비밀번호가 없는 계정은 임시 비밀번호를 발급하지 않는다.
        // 발급하면 소셜 계정에 이메일·비밀번호 로그인이 열린다.
        // 상태를 바꾸지 않고 끝나도록 락과 발급 제한 확인보다 먼저 거부한다.
        if (!StringUtils.hasText(user.getPassword())) {
            throw new PasswordResetNotAllowedException();
        }

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

        // DB에는 해시만 저장한다.
        TemporaryPassword issued = TemporaryPassword.issue(user.getId(), passwordEncoder.encode(rawPassword), expiredAt);
        temporaryPasswordRepository.save(issued);

        // 원문은 커밋이 끝난 뒤 메일로만 전달한다. 롤백되면 이벤트도 전달되지 않는다.
        eventPublisher.publishEvent(new TemporaryPasswordIssuedEvent(
                issued.getId(), user.getEmail(), rawPassword, expiredAt));
    }

    /**
     * 같은 계정의 발급 횟수를 제한한다.
     * 최근 1시간 발급 기록을 최대 5개 가져온다. 5건이면 시간당 제한, 1건 이상이면 가장 최근 발급으로 재요청 간격을 판단한다.
     * 두 제한에 모두 걸리면 더 늦게 풀리는 시각으로 안내한다.
     */
    private void checkIssueLimit(UUID userId, LocalDateTime now) {
        List<TemporaryPassword> recentIssues =
                temporaryPasswordRepository.findTop5ByUserIdAndCreatedAtAfterOrderByCreatedAtDesc(userId, now.minus(ISSUE_WINDOW));

        // 1. 최근 1시간 발급 이력이 없으면 바로 발급한다.
        if (recentIssues.isEmpty()) {
            return;
        }

        LocalDateTime resendAllowedAt = recentIssues.get(0).getCreatedAt().plus(RESEND_INTERVAL);

        // 2. 최근 1시간에 5건이면 시간당 한도에 도달했다.
        //    재시도 시각은 1분 간격 제한과 비교해 더 늦게 풀리는 쪽으로 안내한다.
        if (recentIssues.size() >= MAX_ISSUES_PER_HOUR) {
            LocalDateTime windowAllowedAt =
                    recentIssues.get(MAX_ISSUES_PER_HOUR - 1).getCreatedAt().plus(ISSUE_WINDOW);
            throw new PasswordResetTooManyRequestsException(
                    secondsUntil(now, later(resendAllowedAt, windowAllowedAt))
            );
        }

        //3. 5건 미만이면 가장 최근 발급에서 1분이 지났는지만 확인 한다.
        if (now.isBefore(resendAllowedAt)) {
            throw new PasswordResetTooManyRequestsException(secondsUntil(now, resendAllowedAt));
        }
    }

    /**
     * 안내 메일을 보내지 못한 임시 비밀번호를 폐기한다.
     *
     * <p>발급 트랜잭션이 커밋된 뒤에 호출되므로 항상 새 트랜잭션에서 실행한다.
     * 커밋 후 단계에서 기존 트랜잭션에 참여하면 변경이 반영되지 않는다.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void discardUndeliverable(UUID temporaryPasswordId) {
        temporaryPasswordRepository.findById(temporaryPasswordId)
                .ifPresent(TemporaryPassword::discard);
    }

    // 남은 시간을 초 단위로 올림한다.
    private long secondsUntil(LocalDateTime now, LocalDateTime target) {
        long millis = Duration.between(now, target).toMillis();
        return (millis + 999) / 1000;
    }

    private LocalDateTime later(LocalDateTime a, LocalDateTime b) {
        return a.isAfter(b) ? a : b;
    }

    /** 안내 메일을 보내기 직전에, 그 사이 재발급 등으로 폐기되지 않았는지 확인한다. */
    @Transactional(readOnly = true)
    public boolean isActive(UUID temporaryPasswordId) {
        return true; // [red] GREEN에서 저장된 상태로 판단한다.
    }

}
