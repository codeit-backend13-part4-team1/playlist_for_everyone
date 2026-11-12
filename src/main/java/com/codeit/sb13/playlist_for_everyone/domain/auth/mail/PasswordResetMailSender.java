package com.codeit.sb13.playlist_for_everyone.domain.auth.mail;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * 임시 비밀번호 안내 메일을 보낸다.
 * 발송에 실패하면 MailException을 그래도 던져 호출한 쪽 트랜잭션이 롤백되게 한다.
 */
@Component
public class PasswordResetMailSender {

    static final String SUBJECT = "[모두의 플리] 임시 비밀번호 안내";

    private final JavaMailSender  mailSender;
    private final String from;

    public PasswordResetMailSender(JavaMailSender mailSender,
                                   @Value("${spring.mail.username:}") String from) {
        this.mailSender = mailSender;
        this.from = from;
    }

    public void send(String to, String temporaryPassword, Duration validity) {
        // RED: 다음 단계에서 구현
    }

}
