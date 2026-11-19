package com.codeit.sb13.playlist_for_everyone.domain.user.service;

import com.codeit.sb13.playlist_for_everyone.domain.auth.security.SecurityUser;
import com.codeit.sb13.playlist_for_everyone.domain.user.entity.Role;
import com.codeit.sb13.playlist_for_everyone.domain.user.entity.User;
import com.codeit.sb13.playlist_for_everyone.domain.user.repository.UserRepository;
import com.codeit.sb13.playlist_for_everyone.global.file.service.FileService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Import;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verifyNoInteractions;

@SpringJUnitConfig(UserProfileMethodSecurityTest.TestConfig.class)
@DisplayName("프로필 변경 메서드 인가")
class UserProfileMethodSecurityTest {

    private static final UUID USER_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID OTHER_USER_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");

    @Autowired
    private UserService userService;

    @MockitoBean
    private UserRepository userRepository;

    @MockitoBean
    private PasswordEncoder passwordEncoder;

    @MockitoBean
    private FileService fileService;

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @ParameterizedTest
    @EnumSource(Role.class)
    @DisplayName("다른 사용자의 프로필은 관리자도 변경할 수 없다.")
    void 다른_사용자의_프로필은_변경할_수_없다(Role role) {
        loginAs(USER_ID, role);

        assertThatThrownBy(() -> userService.updateProfile(OTHER_USER_ID, "새 이름", null))
                .isInstanceOf(AccessDeniedException.class);

        verifyNoInteractions(userRepository, fileService);
    }

    @Test
    @DisplayName("본인 프로필은 변경할 수 있다.")
    void 본인_프로필은_변경할_수_있다() {
        loginAs(USER_ID, Role.USER);
        User user = User.builder()
                .email("user@test.com")
                .name("기존 이름")
                .password("encoded-password")
                .role(Role.USER)
                .build();
        ReflectionTestUtils.setField(user, "id", USER_ID);
        given(userRepository.findWithProfileImageById(USER_ID)).willReturn(Optional.of(user));

        assertThatCode(() -> userService.updateProfile(USER_ID, "새 이름", null))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("인증 정보가 없으면 프로필을 변경할 수 없다.")
    void 인증_정보가_없으면_변경할_수_없다() {
        assertThatThrownBy(() -> userService.updateProfile(USER_ID, "새 이름", null))
                .isInstanceOf(AuthenticationCredentialsNotFoundException.class);

        verifyNoInteractions(userRepository, fileService);
    }

    // JWT 필터가 만드는 것과 같은 SecurityUser로 로그인 상태를 만든다.
    private void loginAs(UUID userId, Role role) {
        SecurityUser principal = new SecurityUser(userId, "user@test.com", "", role, false);
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated(principal, null, principal.getAuthorities())
        );
    }

    @TestConfiguration(proxyBeanMethods = false)
    @EnableMethodSecurity
    @Import(UserService.class)
    static class TestConfig {}

}
