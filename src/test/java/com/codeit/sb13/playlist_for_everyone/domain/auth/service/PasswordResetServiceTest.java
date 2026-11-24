package com.codeit.sb13.playlist_for_everyone.domain.auth.service;


import com.codeit.sb13.playlist_for_everyone.domain.auth.entity.TemporaryPassword;
import com.codeit.sb13.playlist_for_everyone.domain.auth.entity.TemporaryPasswordStatus;
import com.codeit.sb13.playlist_for_everyone.domain.auth.event.TemporaryPasswordIssuedEvent;
import com.codeit.sb13.playlist_for_everyone.domain.auth.repository.TemporaryPasswordRepository;
import com.codeit.sb13.playlist_for_everyone.domain.user.entity.Role;
import com.codeit.sb13.playlist_for_everyone.domain.user.entity.User;
import com.codeit.sb13.playlist_for_everyone.domain.user.repository.UserRepository;
import com.codeit.sb13.playlist_for_everyone.global.exception.auth.PasswordResetNotAllowedException;
import com.codeit.sb13.playlist_for_everyone.global.exception.auth.PasswordResetTooManyRequestsException;
import com.codeit.sb13.playlist_for_everyone.global.exception.user.UserNotFoundException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
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
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("비밀번호 초기화")
class PasswordResetServiceTest {

    private static final UUID USER_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID TEMPORARY_PASSWORD_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
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
    private ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private PasswordResetService passwordResetService;



    @Test
    @DisplayName("임시 비밀번호를 해시로 저장하고 원문은 발급 이벤트로 전달한다.")
    void 임시_비밀번호를_해시로_저장하고_원문은_발급_이벤트로_전달한다() {
        givenUserExists();
        given(temporaryPasswordGenerator.generate()).willReturn(RAW_PASSWORD);
        given(passwordEncoder.encode(RAW_PASSWORD)).willReturn(ENCODED_PASSWORD);

        given(temporaryPasswordRepository.save(any(TemporaryPassword.class))).willAnswer(invocation -> {
            TemporaryPassword issued = invocation.getArgument(0);
            ReflectionTestUtils.setField(issued, "id", TEMPORARY_PASSWORD_ID);
            return issued;
        });

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
        assertThat(saved.getExpiredAt()).isBetween(before.plusMinutes(3), after.plusMinutes(3));

        ArgumentCaptor<TemporaryPasswordIssuedEvent> eventCaptor =
                ArgumentCaptor.forClass(TemporaryPasswordIssuedEvent.class);
        verify(eventPublisher).publishEvent(eventCaptor.capture());
        TemporaryPasswordIssuedEvent event = eventCaptor.getValue();
        assertThat(event.temporaryPasswordId()).isEqualTo(TEMPORARY_PASSWORD_ID);
        assertThat(event.email()).isEqualTo(EMAIL);
        assertThat(event.temporaryPassword()).isEqualTo(RAW_PASSWORD);
        assertThat(event.validity()).isEqualTo(Duration.ofMinutes(3));
    }

    @Test
    @DisplayName("저장한 뒤에 발급 이벤트를 발행한다.")
    void 저장한_뒤에_발급_이벤트를_발행한다() {
        givenUserExists();
        given(temporaryPasswordGenerator.generate()).willReturn(RAW_PASSWORD);
        given(passwordEncoder.encode(RAW_PASSWORD)).willReturn(ENCODED_PASSWORD);

        passwordResetService.resetPassword(EMAIL);

        InOrder inOrder = inOrder(temporaryPasswordRepository, eventPublisher);
        inOrder.verify(temporaryPasswordRepository).save(any(TemporaryPassword.class));
        inOrder.verify(eventPublisher).publishEvent(any(TemporaryPasswordIssuedEvent.class));
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

        assertThat(first.getStatus()).isEqualTo(TemporaryPasswordStatus.DISCARDED);
        assertThat(second.getStatus()).isEqualTo(TemporaryPasswordStatus.DISCARDED);
    }

    @Test
    @DisplayName("가입하지 않은 이메일이면 예외가 발생하고 아무것도 발급하지 않는다.")
    void 가입하지_않은_이메일이면_예외가_발생한다() {
        given(userRepository.findByEmail(EMAIL)).willReturn(Optional.empty());

        assertThatThrownBy(() -> passwordResetService.resetPassword(EMAIL))
                .isInstanceOf(UserNotFoundException.class);

        verifyNoInteractions(temporaryPasswordRepository, temporaryPasswordGenerator, eventPublisher);
    }

    @Test
    @DisplayName("1분 안에 다시 요청하면 거부하고 발급하지 않는다.")
    void 일분_안에_다시_요청하면_거부한다() {
        givenUserExists();
        givenRecentIssues(LocalDateTime.now().minusSeconds(30));

        assertThatThrownBy(()-> passwordResetService.resetPassword(EMAIL))
                .isInstanceOf(PasswordResetTooManyRequestsException.class);

        verify(temporaryPasswordRepository, never()).save(any(TemporaryPassword.class));
        verifyNoInteractions(temporaryPasswordGenerator, eventPublisher);
    }

    @Test
    @DisplayName("1분이 지나면 다시 발급한다.")
    void 일분이_지나면_다시_발급한다() {
        givenUserExists();
        givenRecentIssues(LocalDateTime.now().minusSeconds(61));
        given(temporaryPasswordGenerator.generate()).willReturn(RAW_PASSWORD);
        given(passwordEncoder.encode(RAW_PASSWORD)).willReturn(ENCODED_PASSWORD);

        passwordResetService.resetPassword(EMAIL);

        verify(eventPublisher).publishEvent(any(TemporaryPasswordIssuedEvent.class));
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

        verifyNoInteractions(temporaryPasswordGenerator, eventPublisher);
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

        verify(eventPublisher).publishEvent(any(TemporaryPasswordIssuedEvent.class));
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

    @Test
    @DisplayName("비밀번호가 없는 소셜 가입 계정이면 거부하고 아무것도 발급하지 않는다.")
    void 비밀번호가_없는_소셜_가입_계정이면_거부하고_아무것도_발급하지_않는다() {
        givenSocialUserExists();

        assertThatThrownBy(() -> passwordResetService.resetPassword(EMAIL))
                .isInstanceOf(PasswordResetNotAllowedException.class);

        // 락을 잡거나 발급 제한을 확인하기 전에 끝나야 한다.
        verify(userRepository, never()).findByIdForUpdate(any());
        verifyNoInteractions(temporaryPasswordRepository, temporaryPasswordGenerator,
                passwordEncoder, eventPublisher);
    }

    @Test
    @DisplayName("메일을 보내지 못한 임시 비밀번호가 아직 사용 가능하면 폐기한다.")
    void 메일을_보내지_못한_임시_비밀번호가_아직_사용_가능하면_폐기한다() {
        TemporaryPassword issued = TemporaryPassword.issue(USER_ID, ENCODED_PASSWORD, LocalDateTime.now().plusMinutes(3));
        given(temporaryPasswordRepository.findById(TEMPORARY_PASSWORD_ID)).willReturn(Optional.of(issued));

        passwordResetService.discardUndeliverable(TEMPORARY_PASSWORD_ID);

        assertThat(issued.getStatus()).isEqualTo(TemporaryPasswordStatus.DISCARDED);
    }

    @Test
    @DisplayName("폐기할 임시 비밀번호가 없으면 아무것도 하지 않는다.")
    void 폐기할_임시_비밀번호가_없으면_아무것도_하지_않는다() {
        given(temporaryPasswordRepository.findById(TEMPORARY_PASSWORD_ID)).willReturn(Optional.empty());

        passwordResetService.discardUndeliverable(TEMPORARY_PASSWORD_ID);

        verify(temporaryPasswordRepository, never()).save(any());
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

    private void givenSocialUserExists() {
        User user = User.builder()
                .email(EMAIL)
                .name("소셜 사용자")
                .password(null)
                .role(Role.USER)
                .build();
        ReflectionTestUtils.setField(user, "id", USER_ID);
        given(userRepository.findByEmail(EMAIL)).willReturn(Optional.of(user));
    }

}
