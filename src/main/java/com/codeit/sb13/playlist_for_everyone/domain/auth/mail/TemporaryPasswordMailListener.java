package com.codeit.sb13.playlist_for_everyone.domain.auth.mail;

import com.codeit.sb13.playlist_for_everyone.domain.auth.config.PasswordResetMailAsyncConfig;
import com.codeit.sb13.playlist_for_everyone.domain.auth.event.TemporaryPasswordIssuedEvent;
import com.codeit.sb13.playlist_for_everyone.domain.auth.service.PasswordResetService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.retry.RetryPolicy;
import org.springframework.core.retry.RetryTemplate;
import org.springframework.core.task.TaskExecutor;
import org.springframework.mail.MailException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.concurrent.RejectedExecutionException;

/**
 * 임시 비밀번호 발급 이벤트를 받아 안내 메일을 보낸다.
 *
 * <p>발급 트랜잭션이 커밋된 뒤 메일 전용 실행기에 넘겨 보낸다. 롤백되면 보내지 않는다.
 * 실행기가 작업을 받지 못하면 요청 스레드에서 대신 보내지 않고 바로 폐기한다.
 * 보내기 직전마다 그 사이 재발급으로 폐기되지 않았는지 확인하고, 폐기됐으면 보내지 않는다.
 * 보내기 직전마다 남은 사용 시간을 계산해 메일에 적고, 1분도 남지 않았으면 보내지 않고 폐기한다.
 * 메일 서버 오류는 1초, 2초 간격으로 최대 두 번 다시 시도한다.
 * 끝내 보내지 못하면 임시 비밀번호가 남지 않도록 폐기한다.
 * 로그에는 임시 비밀번호 ID와 예외 타입만 남기고 이메일과 원문은 남기지 않는다.
 */
@Slf4j
@Component
public class TemporaryPasswordMailListener {

    // 최초 1번 + 재시도 2번 = 총 3번 보낸다.
    static final int MAX_RETRIES = 2;
    // 남은 시간이 이보다 짧으면 사용자가 메일을 받아도 쓸 수 없어 보내지 않는다.
    static final Duration MIN_REMAINING = Duration.ofMinutes(1);

    private final PasswordResetMailSender mailSender;
    private final PasswordResetService passwordResetService;
    private final TaskExecutor mailExecutor;
    private final RetryTemplate retryTemplate;

    @Autowired
    public TemporaryPasswordMailListener(PasswordResetMailSender mailSender,
                                         PasswordResetService passwordResetService,
                                         @Qualifier(PasswordResetMailAsyncConfig.EXECUTOR_NAME) TaskExecutor mailExecutor) {
        this(mailSender, passwordResetService, mailExecutor, Duration.ofSeconds(1));
    }

    TemporaryPasswordMailListener(PasswordResetMailSender mailSender, PasswordResetService passwordResetService,
                                  TaskExecutor mailExecutor, Duration initialDelay) {
        this.mailSender = mailSender;
        this.passwordResetService = passwordResetService;
        this.mailExecutor = mailExecutor;
        this.retryTemplate = new RetryTemplate(RetryPolicy.builder()
                .maxRetries(MAX_RETRIES)
                .delay(initialDelay)
                .multiplier(2)
                // 메일 서버 장애처럼 다시 보내면 성공할 수 있는 오류만 재시도한다.
                .predicate(exception -> exception instanceof MailException)
                .build());
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handle(TemporaryPasswordIssuedEvent event) {
        try {
            mailExecutor.execute(() -> send(event));
        } catch (RejectedExecutionException exception) {
            // 요청 스레드에서 대신 보내면 메일 장애가 API 응답까지 막는다.
            discard(event, exception);
        }
    }

    private void send(TemporaryPasswordIssuedEvent event) {
        try {
            retryTemplate.invoke(() -> {
                // 대기 중에 재발급되면 이 발급은 이미 폐기됐다. 쓸 수 없는 비밀번호는 보내지 않는다.
                if (!passwordResetService.isActive(event.temporaryPasswordId())) {
                    log.info("이미 폐기된 임시 비밀번호라 메일을 보내지 않습니다. temporaryPasswordId={}",
                            event.temporaryPasswordId());
                    return null;
                }
                // 대기열과 재시도 대기에서 보낸 시간만큼 줄어든 남은 시간을 시도마다 다시 계산한다.
                Duration remaining = Duration.between(LocalDateTime.now(), event.expiredAt());
                if (remaining.compareTo(MIN_REMAINING) < 0) {
                    throw new ExpiringTemporaryPasswordException();
                }
                mailSender.send(event.email(), event.temporaryPassword(), roundToMinutes(remaining));
                return null;
            });
        } catch (RuntimeException exception) {
            discard(event, exception);
        }
    }

    // 메일은 분 단위로 안내한다. 버림하면 바로 보낸 메일도 "2분"이 되어 가장 가까운 분으로 맞춘다.
    private static Duration roundToMinutes(Duration remaining) {
        return Duration.ofMinutes(remaining.plusSeconds(30).toMinutes());
    }

    /** 남은 사용 시간이 너무 짧아 메일을 보내지 않을 때 던진다. 메일 오류가 아니므로 재시도하지 않는다. */
    static final class ExpiringTemporaryPasswordException extends RuntimeException {
        ExpiringTemporaryPasswordException() {
            super("임시 비밀번호의 남은 사용 시간이 1분 미만입니다.");
        }
    }

    private void discard(TemporaryPasswordIssuedEvent event, Exception exception) {
        log.error("임시 비밀번호 안내 메일을 보내지 못해 발급을 폐기합니다. temporaryPasswordId={}, exceptionType={}",
                event.temporaryPasswordId(), exception.getClass().getSimpleName());
        passwordResetService.discardUndeliverable(event.temporaryPasswordId());
    }
}