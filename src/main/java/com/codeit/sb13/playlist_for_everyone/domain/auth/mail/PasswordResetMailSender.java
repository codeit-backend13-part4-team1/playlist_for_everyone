package com.codeit.sb13.playlist_for_everyone.domain.auth.mail;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
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

    private final JavaMailSender mailSender;
    private final String from;

    public PasswordResetMailSender(JavaMailSender mailSender,
                                   @Value("${spring.mail.username:}") String from) {
        this.mailSender = mailSender;
        this.from = from;
    }

    public void send(String to, String temporaryPassword, Duration validity) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(from);
        message.setTo(to);
        message.setSubject(SUBJECT);
        message.setText("""
                안녕하세요. 모두의 플리입니다.
                
                요청하신 임시 비밀번호를 안내해 드립니다.
                
                임시 비밀번호 %s
                
                임시 비밀번호는 %d분 동안만 사용할 수 있습니다.
                로그인한 뒤 비밀번호를 변경해 주세요.
                """.formatted(temporaryPassword, validity.toMinutes()));

        // 실패하면 MailException이 그대로 전파되어 호출한 쪽 트랜잭션이 롤백된다.
        mailSender.send(message);
    }
}
