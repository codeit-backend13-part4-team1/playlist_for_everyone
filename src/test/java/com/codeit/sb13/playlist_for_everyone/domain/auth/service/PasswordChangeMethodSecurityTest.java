package com.codeit.sb13.playlist_for_everyone.domain.auth.service;

import com.codeit.sb13.playlist_for_everyone.domain.auth.repository.TemporaryPasswordRepository;
import com.codeit.sb13.playlist_for_everyone.domain.auth.security.SecurityUser;
import com.codeit.sb13.playlist_for_everyone.domain.user.entity.Role;
import com.codeit.sb13.playlist_for_everyone.domain.user.entity.User;
import com.codeit.sb13.playlist_for_everyone.domain.user.repository.UserRepository;
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

@SpringJUnitConfig(PasswordChangeMethodSecurityTest.TestConfig.class)
@DisplayName("비밀번호 변경 메서드 인가")
class PasswordChangeMethodSecurityTest {

    private static final UUID USER_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID OTHER_USER_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final String NEW_PASSWORD = "new-password";


    @Autowired
    private PasswordChangeService passwordChangeService;

    @MockitoBean
    private UserRepository userRepository;

    @MockitoBean
    private TemporaryPasswordRepository temporaryPasswordRepository;

    @MockitoBean
    private PasswordEncoder passwordEncoder;

    @AfterEach
    void cleanUp() {
        SecurityContextHolder.clearContext();
    }

    @ParameterizedTest
    @EnumSource(Role.class)
    @DisplayName("다른 사용자의 비밀번호는 관리자도 변경할 수 없다.")
    void 다른_사용자의_비밀번호는_변경할_수_없다(Role role) {
        loginAs(USER_ID, role);

        assertThatThrownBy(() -> passwordChangeService.changePassword(OTHER_USER_ID, NEW_PASSWORD))
                .isInstanceOf(AccessDeniedException.class);

        verifyNoInteractions(userRepository, temporaryPasswordRepository, passwordEncoder);
    }

    @Test
    @DisplayName("본인 비밀번호는 변경할 수 있다.")
    void 본인_비밀번호는_변경할_수_있다() {
        loginAs(USER_ID, Role.USER);
        User user = User.builder()
                .email("user@test.com")
                .name("사용자")
                .password("encoded-old-password")
                .role(Role.USER)
                .build();
        ReflectionTestUtils.setField(user, "id", USER_ID);
        given(userRepository.findByIdForUpdate(USER_ID)).willReturn(Optional.of(user));
        given(passwordEncoder.encode(NEW_PASSWORD)).willReturn("encoded-new-password");

        assertThatCode(() -> passwordChangeService.changePassword(USER_ID, NEW_PASSWORD))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("인증 정보가 없으면 비밀번호를 변경할 수 없다.")
    void 인증_정보가_없으면_변경할_수_없다() {
        assertThatThrownBy(() -> passwordChangeService.changePassword(USER_ID, NEW_PASSWORD))
                .isInstanceOf(AuthenticationCredentialsNotFoundException.class);

        verifyNoInteractions(userRepository, temporaryPasswordRepository, passwordEncoder);
    }



    // JWT 필터가 만드는 것과 같은 SecurityUser로 로그인 상태를 만든다.
    // @WithMockUser의 기본 사용자에는 userId가 없어 SpEL을 검증할 수 없다.
    private void loginAs(UUID userId, Role role) {
        SecurityUser principal = new SecurityUser(userId, "user@test.com", "", role, false);
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated(principal, null, principal.getAuthorities()));
    }



    @TestConfiguration(proxyBeanMethods = false)
    @EnableMethodSecurity
    @Import(PasswordChangeService.class)
    static class TestConfig{}
}
