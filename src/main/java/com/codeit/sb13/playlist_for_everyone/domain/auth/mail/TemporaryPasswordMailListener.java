package com.codeit.sb13.playlist_for_everyone.domain.auth.mail;

import com.codeit.sb13.playlist_for_everyone.domain.auth.event.TemporaryPasswordIssuedEvent;
import com.codeit.sb13.playlist_for_everyone.domain.auth.service.PasswordResetService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * 임시 비밀번호 발급 이벤트를 받아 안내 메일을 보낸다.
 */
@Component
public class TemporaryPasswordMailListener {

    private final PasswordResetMailSender mailSender;
    private final PasswordResetService passwordResetService;

    @Autowired
    public TemporaryPasswordMailListener(PasswordResetMailSender mailSender, PasswordResetService passwordResetService) {
        this(mailSender, passwordResetService, Duration.ofSeconds(1));
    }

    TemporaryPasswordMailListener(PasswordResetMailSender mailSender, PasswordResetService passwordResetService, Duration initialDelay) {
        this.mailSender = mailSender;
        this.passwordResetService = passwordResetService;
    }

    public void handle(TemporaryPasswordIssuedEvent event) {
        // 그린에서 구현
    }
}
