package com.codeit.sb13.playlist_for_everyone.domain.auth.service;


import com.codeit.sb13.playlist_for_everyone.domain.auth.entity.TemporaryPassword;
import com.codeit.sb13.playlist_for_everyone.domain.auth.entity.TemporaryPasswordStatus;
import com.codeit.sb13.playlist_for_everyone.domain.auth.mail.PasswordResetMailSender;
import com.codeit.sb13.playlist_for_everyone.domain.auth.repository.TemporaryPasswordRepository;
import com.codeit.sb13.playlist_for_everyone.domain.user.entity.Role;
import com.codeit.sb13.playlist_for_everyone.domain.user.entity.User;
import com.codeit.sb13.playlist_for_everyone.domain.user.repository.UserRepository;
import com.codeit.sb13.playlist_for_everyone.global.exception.auth.PasswordResetTooManyRequestsException;
import com.codeit.sb13.playlist_for_everyone.global.exception.user.UserNotFoundException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.MailSendException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("비밀번호 초기화")
class PasswordResetServiceTest {

    private static final UUID USER_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final String EMAIL = "user@test.com";
    private static final String RAW_PASSWORD = "Ab3!Cd4@Ef5#";
    private static final String ENCODED_PASSWORD = "encoded-temporary-password";

    @Mock
    private UserRepository userRepository;

    @Mock
    private TemporaryPasswordRepository temporaryPasswordRepository;

    @Mock
    private TemporaryPasswordGenerator temporaryPasswordGenerator;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private PasswordResetMailSender passwordResetMailSender;

    @InjectMocks
    private PasswordResetService passwordResetService;



    @Test
    @DisplayName("임시 비밀번호를 해시로 저장하고 원문을 메일로 보낸다.")
    void 임시_비밀번호를_해시로_저장하고_메일로_보낸다() {
        givenUserExists();
        given(temporaryPasswordGenerator.generate()).willReturn(RAW_PASSWORD);
        given(passwordEncoder.encode(RAW_PASSWORD)).willReturn(ENCODED_PASSWORD);

        LocalDateTime before = LocalDateTime.now();
        passwordResetService.resetPassword(EMAIL);
        LocalDateTime after = LocalDateTime.now();

        ArgumentCaptor<TemporaryPassword> captor = ArgumentCaptor.forClass(TemporaryPassword.class);
        verify(temporaryPasswordRepository).save(captor.capture());
        TemporaryPassword saved = captor.getValue();

        assertThat(saved.getUserId()).isEqualTo(USER_ID);
        // DB에는 원문이 아닌 해시만 저장한다.
        assertThat(saved.getPassword()).isEqualTo(ENCODED_PASSWORD);
        assertThat(saved.getStatus()).isEqualTo(TemporaryPasswordStatus.ACTIVE);
        // 실행 시각 + 3분 범위 안이어야 한다.
        assertThat(saved.getExpiredAt()).isBetween(before.plusMinutes(3), after.plusMinutes(3));

        // 메일에는 원문과 같은 유효 시간을 보낸다.
        verify(passwordResetMailSender).send(EMAIL, RAW_PASSWORD, Duration.ofMinutes(3));
    }

    @Test
    @DisplayName("저장한 뒤에 메일을 보낸다.")
    void 저장한_뒤에_메일을_보낸다() {
        givenUserExists();
        given(temporaryPasswordGenerator.generate()).willReturn(RAW_PASSWORD);
        given(passwordEncoder.encode(RAW_PASSWORD)).willReturn(ENCODED_PASSWORD);

        passwordResetService.resetPassword(EMAIL);

        InOrder inOrder = inOrder(temporaryPasswordRepository, passwordResetMailSender);
        inOrder.verify(temporaryPasswordRepository).save(any(TemporaryPassword.class));
        inOrder.verify(passwordResetMailSender).send(anyString(), anyString(), any(Duration.class));
    }

    @Test
    @DisplayName("이미 발급된 사용 가능한 임시 비밀번호는 파기한다.")
    void 이미_발급된_임시_비밀번호는_파기한다() {
        givenUserExists();
        TemporaryPassword first = TemporaryPassword.issue(USER_ID, "old-1", LocalDateTime.now().plusMinutes(1));
        TemporaryPassword second = TemporaryPassword.issue(USER_ID, "old-2", LocalDateTime.now().plusMinutes(2));
        given(temporaryPasswordRepository.findAllByUserIdAndStatus(USER_ID, TemporaryPasswordStatus.ACTIVE))
                .willReturn(List.of(first, second));
        given(temporaryPasswordGenerator.generate()).willReturn(RAW_PASSWORD);
        given(passwordEncoder.encode(RAW_PASSWORD)).willReturn(ENCODED_PASSWORD);

        passwordResetService.resetPassword(EMAIL);

        InOrder inOrder = inOrder(temporaryPasswordRepository, passwordResetMailSender);

        assertThat(first.getStatus()).isEqualTo(TemporaryPasswordStatus.DISCARDED);
        assertThat(second.getStatus()).isEqualTo(TemporaryPasswordStatus.DISCARDED);
    }

    @Test
    @DisplayName("가입하지 않은 이메일이면 예외가 발생하고 아무것도 발급하지 않는다.")
    void 가입하지_않은_이메일이면_예외가_발생한다() {
        given(userRepository.findByEmail(EMAIL)).willReturn(Optional.empty());

        assertThatThrownBy(() -> passwordResetService.resetPassword(EMAIL))
                .isInstanceOf(UserNotFoundException.class);

        verifyNoInteractions(temporaryPasswordRepository, temporaryPasswordGenerator, passwordResetMailSender);
    }

    @Test
    @DisplayName("메일 발송에 실패하면 예외를 그대로 전파한다.")
    void 메일_발송에_실패하면_예외를_전파한다() {
        givenUserExists();
        given(temporaryPasswordGenerator.generate()).willReturn(RAW_PASSWORD);
        given(passwordEncoder.encode(RAW_PASSWORD)).willReturn(ENCODED_PASSWORD);
        willThrow(new MailSendException("SMTP 연결 실패"))
                .given(passwordResetMailSender).send(anyString(), anyString(), any(Duration.class));

        // 예외가 전파되어야 @Transactional이 저장을 롤백한다. 롤백 자체는 통합 테스트에서 확인한다.
        assertThatThrownBy(() -> passwordResetService.resetPassword(EMAIL))
                .isInstanceOf(MailSendException.class);
    }

    @Test
    @DisplayName("1분 안에 다시 요청하면 거부하고 발급하지 않는다.")
    void 일분_안에_다시_요청하면_거부한다() {
        givenUserExists();
        givenRecentIssues(LocalDateTime.now().minusSeconds(30));

        assertThatThrownBy(()-> passwordResetService.resetPassword(EMAIL))
                .isInstanceOf(PasswordResetTooManyRequestsException.class);

        verify(temporaryPasswordRepository, never()).save(any(TemporaryPassword.class));
        verifyNoInteractions(temporaryPasswordGenerator, passwordResetMailSender);
    }

    @Test
    @DisplayName("1분이 지나면 다시 발급한다.")
    void 일분이_지나면_다시_발급한다() {
        givenUserExists();
        givenRecentIssues(LocalDateTime.now().minusSeconds(61));
        given(temporaryPasswordGenerator.generate()).willReturn(RAW_PASSWORD);
        given(passwordEncoder.encode(RAW_PASSWORD)).willReturn(ENCODED_PASSWORD);

        passwordResetService.resetPassword(EMAIL);

        verify(passwordResetMailSender).send(EMAIL, RAW_PASSWORD, Duration.ofMinutes(3));
    }

    @Test
    @DisplayName("최근 1시간 안에 5번 발급했으면 거부한다.")
    void 최근_한_시간_안에_다섯_번이면_거부한다() {
        givenUserExists();
        LocalDateTime now = LocalDateTime.now();
        givenRecentIssues(
                now.minusMinutes(2), now.minusMinutes(10), now.minusMinutes(20),
                now.minusMinutes(30), now.minusMinutes(50));

        assertThatThrownBy(() -> passwordResetService.resetPassword(EMAIL))
                .isInstanceOf(PasswordResetTooManyRequestsException.class);

        verifyNoInteractions(temporaryPasswordGenerator, passwordResetMailSender);
    }

    @Test
    @DisplayName("가장 오래된 발급이 1시간 밖으로 나가면 다시 발급한다.")
    void 가장_오래된_발급이_한_시간_밖이면_다시_발급한다() {
        givenUserExists();
        LocalDateTime now = LocalDateTime.now();
        // 61분 전 발급은 1시간 밖이라 조회되지 않는다. 최근 1시간에는 4건만 남는다.
        givenRecentIssues(
                now.minusMinutes(2), now.minusMinutes(10),
                now.minusMinutes(20), now.minusMinutes(30));
        given(temporaryPasswordGenerator.generate()).willReturn(RAW_PASSWORD);
        given(passwordEncoder.encode(RAW_PASSWORD)).willReturn(ENCODED_PASSWORD);

        passwordResetService.resetPassword(EMAIL);

        verify(passwordResetMailSender).send(EMAIL, RAW_PASSWORD, Duration.ofMinutes(3));
    }

    @Test
    @DisplayName("5건 제한에 걸려도 1분 제한이 더 늦게 풀리면 그 시각으로 안내한다.")
    void 다섯_건_제한에서도_일분_제한이_더_늦으면_그_시각으로_안내한다() {
        givenUserExists();
        LocalDateTime now = LocalDateTime.now();
        // 가장 최근: 10초 전 → 1분 제한은 50초 뒤 / 5번째: 59분 50초 전 → 1시간 제한은 10초 뒤
        givenRecentIssues(
                now.minusSeconds(10), now.minusMinutes(20), now.minusMinutes(30),
                now.minusMinutes(40), now.minusMinutes(59).minusSeconds(50));

        assertThatThrownBy(() -> passwordResetService.resetPassword(EMAIL))
                .isInstanceOfSatisfying(PasswordResetTooManyRequestsException.class, exception ->
                        assertThat((Long) exception.getDetails().get("retryAfterSeconds"))
                                .isBetween(45L, 50L));
    }

    @Test
    @DisplayName("두 제한에 모두 걸리면 더 늦게 풀리는 시각으로 재시도 시간을 안내한다.")
    void 두_제한에_모두_걸리면_더_늦은_시각으로_안내한다(){
        givenUserExists();
        LocalDateTime now = LocalDateTime.now();
        // 가장 최근: 30초 전 -> 1분 제한은 30초 뒤 해제
        // 5번째: 10분 전 -> 1시간 제한은 50분 뒤 해제(이쪽이 더 늦다)
        givenRecentIssues(
                now.minusSeconds(30), now.minusMinutes(2), now.minusMinutes(3),
                now.minusMinutes(4), now.minusMinutes(10)
        );

        assertThatThrownBy(() -> passwordResetService.resetPassword(EMAIL))
                .isInstanceOfSatisfying(PasswordResetTooManyRequestsException.class, exception ->
                        assertThat((Long) exception.getDetails().get("retryAfterSeconds"))
                                .isBetween(2990L, 3000L));
    }


    private void givenUserExists() {
        User user = User.builder()
                .email(EMAIL)
                .name("사용자")
                .password("encoded-original-password")
                .role(Role.USER)
                .build();
        ReflectionTestUtils.setField(user, "id", USER_ID);
        given(userRepository.findByEmail(EMAIL)).willReturn(Optional.of(user));
    }

    private void givenRecentIssues(LocalDateTime... createdAts) {
        List<TemporaryPassword> issues = Arrays.stream(createdAts)
                .map(createdAt -> {
                    TemporaryPassword issued = TemporaryPassword.issue(USER_ID, "old", createdAt.plusMinutes(3));
                    issued.discard();
                    ReflectionTestUtils.setField(issued, "createdAt", createdAt);
                    return issued;
                })
                .toList();
        given(temporaryPasswordRepository
                .findTop5ByUserIdAndCreatedAtAfterOrderByCreatedAtDesc(eq(USER_ID), any(LocalDateTime.class)))
                .willReturn(issues);
    }

}
