package com.codeit.sb13.playlist_for_everyone.domain.auth.mail;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("임시 비밀번호 메일 발송")
class PasswordResetMailSenderTest {

    private static final String FROM = "mopl.team@test.com";
    private static final String TO = "user@test.com";
    private static final String TEMPORARY_PASSWORD = "Ab3!Cd4@Ef5#";

    @Mock
    private JavaMailSender javamailSender;

    private PasswordResetMailSender mailSender;

    @BeforeEach
    void setUp() {
        mailSender = new PasswordResetMailSender(javamailSender, FROM);
    }

    @Test
    @DisplayName("받는 사람에게 임시 비밀번호와 유효 시간을 담아 보낸다.")
    void 임시_비밀번호_유효_시간을_땀아_보낸다() {
        mailSender.send(TO, TEMPORARY_PASSWORD, Duration.ofMinutes(3));

        ArgumentCaptor<SimpleMailMessage> captor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(javamailSender).send(captor.capture());

        SimpleMailMessage message = captor.getValue();
        assertThat(message.getFrom()).isEqualTo(FROM);
        assertThat(message.getTo()).containsExactly(TO);
        assertThat(message.getSubject()).isEqualTo(PasswordResetMailSender.SUBJECT);
        assertThat(message.getText()).contains(TEMPORARY_PASSWORD).contains("3분");
    }

    @Test
    @DisplayName("발송에 실패하면 예외를 그래도 던진다")
    void 발송에_실패하면_예외를_그대로_던진다() {
        willThrow(new MailSendException("SMTP 연결 실패"))
                .given(javamailSender).send(any(SimpleMailMessage.class));

        // 서비스 트랜잭션이 롤백되어야 하므로 여기서 예외를 삼키지 않는다.
        assertThatThrownBy(() -> mailSender.send(TO, TEMPORARY_PASSWORD, Duration.ofMinutes(3)))
                .isInstanceOf(MailSendException.class);
    }
}
