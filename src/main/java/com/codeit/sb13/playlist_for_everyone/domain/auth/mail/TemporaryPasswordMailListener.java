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
import java.util.concurrent.RejectedExecutionException;

/**
 * 임시 비밀번호 발급 이벤트를 받아 안내 메일을 보낸다.
 *
 * <p>발급 트랜잭션이 커밋된 뒤 메일 전용 실행기에 넘겨 보낸다. 롤백되면 보내지 않는다.
 * 실행기가 작업을 받지 못하면 요청 스레드에서 대신 보내지 않고 바로 폐기한다.
 * 메일 서버 오류는 1초, 2초 간격으로 최대 두 번 다시 시도한다.
 * 끝내 보내지 못하면 임시 비밀번호가 남지 않도록 폐기한다.
 * 로그에는 임시 비밀번호 ID와 예외 타입만 남기고 이메일과 원문은 남기지 않는다.
 */
@Slf4j
@Component
public class TemporaryPasswordMailListener {

    // 최초 1번 + 재시도 2번 = 총 3번 보낸다.
    static final int MAX_RETRIES = 2;

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
                mailSender.send(event.email(), event.temporaryPassword(), event.validity());
                return null;
            });
        } catch (RuntimeException exception) {
            discard(event, exception);
        }
    }

    private void discard(TemporaryPasswordIssuedEvent event, Exception exception) {
        log.error("임시 비밀번호 안내 메일을 보내지 못해 발급을 폐기합니다. temporaryPasswordId={}, exceptionType={}",
                event.temporaryPasswordId(), exception.getClass().getSimpleName());
        passwordResetService.discardUndeliverable(event.temporaryPasswordId());
    }
}