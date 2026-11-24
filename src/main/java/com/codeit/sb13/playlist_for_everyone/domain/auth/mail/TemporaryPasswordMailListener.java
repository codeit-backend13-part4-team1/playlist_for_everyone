package com.codeit.sb13.playlist_for_everyone.domain.auth.mail;

import com.codeit.sb13.playlist_for_everyone.domain.auth.event.TemporaryPasswordIssuedEvent;
import com.codeit.sb13.playlist_for_everyone.domain.auth.service.PasswordResetService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.retry.RetryPolicy;
import org.springframework.core.retry.RetryTemplate;
import org.springframework.mail.MailException;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * 임시 비밀번호 발급 이벤트를 받아 안내 메일을 보낸다.
 *
 * <p>메일 서버 오류는 1초, 2초 간격으로 최대 두 번 다시 시도한다.
 * 발송 실패시 임시 비밀번호가 남지 않도록 폐기한다.
 * 로그에는 임시 비밀번호 ID와 예외 타입만 남기고 이메일과 원문은 남기지 않는다.
 */
@Slf4j
@Component
public class TemporaryPasswordMailListener {

    // 최초 1번 + 재시도 2번 = 총 3번 보낸다.
    static final int MAX_RETRIES = 2;

    private final PasswordResetMailSender mailSender;
    private final PasswordResetService passwordResetService;
    private final RetryTemplate retryTemplate;

    @Autowired
    public TemporaryPasswordMailListener(PasswordResetMailSender mailSender, PasswordResetService passwordResetService) {
        this(mailSender, passwordResetService, Duration.ofSeconds(1));
    }

    TemporaryPasswordMailListener(PasswordResetMailSender mailSender, PasswordResetService passwordResetService, Duration initialDelay) {
        this.mailSender = mailSender;
        this.passwordResetService = passwordResetService;
        this.retryTemplate = new RetryTemplate(RetryPolicy.builder()
                .maxRetries(MAX_RETRIES)
                .delay(initialDelay)
                .multiplier(2)
                // 메일 서버 장애처럼 다시 보내면 성공할 수 있는 오류만 재시도한다.
                .predicate(exception -> exception instanceof MailException)
                .build());
    }

    public void handle(TemporaryPasswordIssuedEvent event) {
        try {
            retryTemplate.invoke(()-> {
                mailSender.send(event.email(), event.temporaryPassword(), event.validity());
                return null;
            });
        } catch (RuntimeException exception) {
            log.error("임시 비밀번호 안내 메일을 보내지 못해 발급을 폐기합니다. temporaryPasswordId={}, exceptionType={}",
                    event.temporaryPasswordId(), exception.getClass().getSimpleName());
            passwordResetService.discardUndeliverable(event.temporaryPasswordId());
        }
    }
}
