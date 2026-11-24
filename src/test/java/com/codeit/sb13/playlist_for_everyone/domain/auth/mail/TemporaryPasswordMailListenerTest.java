package com.codeit.sb13.playlist_for_everyone.domain.auth.mail;

import com.codeit.sb13.playlist_for_everyone.domain.auth.event.TemporaryPasswordIssuedEvent;
import com.codeit.sb13.playlist_for_everyone.domain.auth.service.PasswordResetService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.mail.MailSendException;

import java.time.Duration;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith({MockitoExtension.class, OutputCaptureExtension.class})
@DisplayName("임시 비밀번호 안내 메일 발송")
class TemporaryPasswordMailListenerTest {

    private static final UUID TEMPORARY_PASSWORD_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final String EMAIL = "user@test.com";
    private static final String RAW_PASSWORD = "Ab3!Cd4@Ef5#";
    private static final Duration VALIDITY = Duration.ofMinutes(3);
    private static final TemporaryPasswordIssuedEvent EVENT =
            new TemporaryPasswordIssuedEvent(TEMPORARY_PASSWORD_ID, EMAIL, RAW_PASSWORD, VALIDITY);

    @Mock
    private PasswordResetMailSender mailSender;

    @Mock
    private PasswordResetService passwordResetService;

    private TemporaryPasswordMailListener listener;

    @BeforeEach
    void setUp() {
        // 재시도 대기 없이 바로 다시 시도한다.
        listener = new TemporaryPasswordMailListener(mailSender, passwordResetService, Duration.ZERO);
    }

    @Test
    @DisplayName("이벤트의 이메일, 원문, 유효 시간으로 메일을 보낸다.")
    void 이벤트의_이메일_원문_유효_시간으로_메일을_보낸다() {
        listener.handle(EVENT);

        verify(mailSender).send(EMAIL, RAW_PASSWORD, VALIDITY);
        verifyNoInteractions(passwordResetService);
    }

    @Test
    @DisplayName("두 번 실패해도 세 번째에 성공하면 임시 비밀번호를 폐기하지 않는다.")
    void 두_번_실패해도_세_번째에_성공하면_임시_비밀번호를_폐기하지_않는다() {
        willThrow(new MailSendException("SMTP 일시 장애"))
                .willThrow(new MailSendException("SMTP 일시 장애"))
                .willDoNothing()
                .given(mailSender).send(anyString(), anyString(), any(Duration.class));

        listener.handle(EVENT);

        verify(mailSender, times(3)).send(EMAIL, RAW_PASSWORD, VALIDITY);
        verify(passwordResetService, never()).discardUndeliverable(any());
    }

    @Test
    @DisplayName("세 번 모두 실패하면 해당 임시 비밀번호를 폐기한다.")
    void 세_번_모두_실패하면_해당_임시_비밀번호를_폐기한다() {
        willThrow(new MailSendException("SMTP 연결 실패"))
                .given(mailSender).send(anyString(), anyString(), any(Duration.class));

        listener.handle(EVENT);

        verify(mailSender, times(3)).send(EMAIL, RAW_PASSWORD, VALIDITY);
        verify(passwordResetService).discardUndeliverable(TEMPORARY_PASSWORD_ID);
    }

    @Test
    @DisplayName("메일 오류가 아니면 재시도하지 않고 바로 폐기한다.")
    void 메일_오류가_아니면_재시도하지_않고_바로_폐기한다() {
        willThrow(new IllegalStateException("메일 설정 오류"))
                .given(mailSender).send(anyString(), anyString(), any(Duration.class));

        listener.handle(EVENT);

        verify(mailSender, times(1)).send(EMAIL, RAW_PASSWORD, VALIDITY);
        verify(passwordResetService).discardUndeliverable(TEMPORARY_PASSWORD_ID);
    }

    @Test
    @DisplayName("실패 로그에는 임시 비밀번호 ID만 남기고 이메일과 원문은 남기지 않는다.")
    void 실패_로그에는_임시_비밀번호_ID만_남기고_이메일과_원문은_남기지_않는다(CapturedOutput output) {
        willThrow(new MailSendException("SMTP 연결 실패"))
                .given(mailSender).send(anyString(), anyString(), any(Duration.class));

        listener.handle(EVENT);

        assertThat(output.getAll())
                .contains(TEMPORARY_PASSWORD_ID.toString())
                .doesNotContain(EMAIL)
                .doesNotContain(RAW_PASSWORD);
    }

    @Test
    @DisplayName("이벤트를 문자열로 바꿔도 이메일과 원문이 드러나지 않는다.")
    void 이벤트를_문자열로_바꿔도_이메일과_원문이_드러나지_않는다() {
        assertThat(EVENT.toString())
                .contains(TEMPORARY_PASSWORD_ID.toString())
                .doesNotContain(EMAIL)
                .doesNotContain(RAW_PASSWORD);
    }
}