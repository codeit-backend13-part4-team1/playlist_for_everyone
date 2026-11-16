package com.codeit.sb13.playlist_for_everyone.domain.user.service;

import com.codeit.sb13.playlist_for_everyone.domain.user.entity.Role;
import com.codeit.sb13.playlist_for_everyone.domain.user.entity.User;
import com.codeit.sb13.playlist_for_everyone.domain.user.event.UserLockedEvent;
import com.codeit.sb13.playlist_for_everyone.domain.user.event.UserRoleChangedEvent;
import com.codeit.sb13.playlist_for_everyone.domain.user.repository.UserRepository;
import com.codeit.sb13.playlist_for_everyone.global.exception.user.UserNotFoundException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserAdministrationServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private UserAdministrationService userAdministrationService;

    @ParameterizedTest
    @EnumSource(Role.class)
    @DisplayName("권한이 변경되면 변경 전 후 권한을 담은 이벤트를 한 번 발행한다")
    void 권한을_변경하고_이벤트를_발행한다(Role newRole) {
        UUID userId = UUID.randomUUID();
        Role previousRole = newRole == Role.ADMIN ? Role.USER : Role.ADMIN;
        User user = createUser(previousRole);

        given(userRepository.findByIdForUpdate(userId))
                .willReturn(Optional.of(user));

        userAdministrationService.updateRole(userId, newRole);

        assertThat(user.getRole()).isEqualTo(newRole);
        verify(eventPublisher).publishEvent(
                new UserRoleChangedEvent(userId, previousRole, newRole)
        );

        verifyNoMoreInteractions(eventPublisher);
    }

    @ParameterizedTest
    @EnumSource(Role.class)
    @DisplayName("현재와 같은 권한을 요청하면 이벤트를 발행하지 않는다.")
    void 동일한_권한이면_이벤트를_발행하지_않는다(Role role) {
        UUID userId = UUID.randomUUID();
        User user = createUser(role);

        given(userRepository.findByIdForUpdate(userId))
                .willReturn(Optional.of(user));

        userAdministrationService.updateRole(userId, role);

        assertThat(user.getRole()).isEqualTo(role);
        verifyNoInteractions(eventPublisher);
    }

    @Test
    @DisplayName("권한 변경 대상 사용자가 없으면 예외가 발생하고 이벤트를 발행하지 않는다")
    void 권한_변경_대상이_없으면_거절한다() {
        UUID userId = UUID.randomUUID();

        given(userRepository.findByIdForUpdate(userId))
                .willReturn(Optional.empty());

        assertThatThrownBy(() ->
                userAdministrationService.updateRole(userId, Role.ADMIN))
                .isInstanceOf(UserNotFoundException.class);

        verifyNoInteractions(eventPublisher);
    }

    @Test
    @DisplayName("권한 변경은 기존 계정의 잠금 상태를 해제하지 않는다")
    void 권한_변경시_잠금_상태를_유지한다() {
        UUID userId = UUID.randomUUID();
        User user = createUser(Role.USER);
        user.lock();

        given(userRepository.findByIdForUpdate(userId))
                .willReturn(Optional.of(user));

        userAdministrationService.updateRole(userId, Role.ADMIN);

        assertThat(user.getRole()).isEqualTo(Role.ADMIN);
        assertThat(user.isLocked()).isTrue();

        verify(eventPublisher).publishEvent(
                new UserRoleChangedEvent(userId, Role.USER, Role.ADMIN)
        );
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    @DisplayName("현재와 같은 잠금 상태를 요청하면 기존 상태를 유지한다")
    void 동일한_잠금_상태를_유지한다(boolean locked) {
        UUID userId = UUID.randomUUID();
        User user = createUser(Role.USER);

        if (locked) {
            user.lock();
        }

        given(userRepository.findByIdForUpdate(userId))
                .willReturn(Optional.of(user));

        userAdministrationService.updateLocked(userId, locked);

        assertThat(user.isLocked()).isEqualTo(locked);
        verifyNoInteractions(eventPublisher);
    }

    @Test
    @DisplayName("잠금 변경 대상 사용자가 없으면 예외가 발생한다")
    void 잠금_변경_대상이_없으면_거절한다() {
        UUID userId = UUID.randomUUID();

        given(userRepository.findByIdForUpdate(userId))
                .willReturn(Optional.empty());

        assertThatThrownBy(() ->
                userAdministrationService.updateLocked(userId, true))
                .isInstanceOf(UserNotFoundException.class);

        verifyNoInteractions(eventPublisher);
    }

    @Test
    @DisplayName("계정을 잠그면 잠금 이벤트를 한 번 발행한다")
    void 계정을_잠그고_이벤트를_발행한다() {
        UUID userId = UUID.randomUUID();
        User user = createUser(Role.USER);

        given(userRepository.findByIdForUpdate(userId))
                .willReturn(Optional.of(user));

        userAdministrationService.updateLocked(userId, true);

        assertThat(user.isLocked()).isTrue();
        assertThat(user.getRole()).isEqualTo(Role.USER);

        // 인증 도메인이 커밋 후 이 이벤트로 로그인 세션을 종료한다.
        verify(eventPublisher).publishEvent(new UserLockedEvent(userId));
        verifyNoMoreInteractions(eventPublisher);
    }

    @Test
    @DisplayName("계정 잠금을 해제하면 이벤트를 발행하지 않는다")
    void 계정_잠금을_해제하면_이벤트를_발행하지_않는다() {
        UUID userId = UUID.randomUUID();
        User user = createUser(Role.USER);
        user.lock();

        given(userRepository.findByIdForUpdate(userId))
                .willReturn(Optional.of(user));

        userAdministrationService.updateLocked(userId, false);

        assertThat(user.isLocked()).isFalse();
        assertThat(user.getRole()).isEqualTo(Role.USER);

        // 잠금 해제는 로그인 세션을 종료할 이유가 없다.
        verifyNoInteractions(eventPublisher);
    }



    private User createUser(Role role) {
        return User.builder()
                .email("user@gmail.com")
                .name("테스트 사용자")
                .password("encoded-password")
                .role(role)
                .build();
    }
}