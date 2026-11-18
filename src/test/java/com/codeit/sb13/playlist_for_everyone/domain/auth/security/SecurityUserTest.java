package com.codeit.sb13.playlist_for_everyone.domain.auth.security;


import com.codeit.sb13.playlist_for_everyone.domain.user.entity.Role;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.security.core.GrantedAuthority;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("인증 사용자 정보")
class SecurityUserTest {

    @Test
    @DisplayName("사용자 ID와 이메일, 암호문을 제공한다.")
    void 사용자_정보를_제공한다() {

        UUID userId = UUID.randomUUID();

        SecurityUser securityUser = new SecurityUser(
                userId,
                "test@gmail.com",
                "encoded_password",
                Role.USER,
                false
        );

        assertThat(securityUser.getUserId()).isEqualTo(userId);
        assertThat(securityUser.getUsername()).isEqualTo("test@gmail.com");
        assertThat(securityUser.getPassword()).isEqualTo("encoded_password");
        assertThat(securityUser.getRole()).isEqualTo(Role.USER);
    }

    @ParameterizedTest
    @EnumSource(Role.class)
    @DisplayName("역할을 ROLE 접두사가 있는 권한으로 변환한다")
    void 역할을_권한으로_변환한다(Role role) {

        SecurityUser securityUser = new SecurityUser(
                UUID.randomUUID(),
                "test@gmail.com",
                "encoded-password",
                role,
                false
        );

        assertThat(securityUser.getAuthorities())
                .extracting(GrantedAuthority::getAuthority)
                .containsExactly("ROLE_" + role.name());
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    @DisplayName("잠금 상태를 계정 활성 여부와 잠금 여부에 반영한다")
    void 잠금_상태를_반영한다(boolean locked) {

        SecurityUser securityUser = new SecurityUser(
                UUID.randomUUID(),
                "test@gmail.com",
                "encoded-password",
                Role.USER,
                locked
        );

        assertThat(securityUser.isEnabled()).isEqualTo(!locked);
        assertThat(securityUser.isAccountNonLocked()).isEqualTo(!locked);
    }

    @Test
    @DisplayName("인증 자격 증명을 제거해도 사용자 ID는 유지한다.")
    void 암호문을_제거해도_사용자_ID는_유지한다() {

        UUID userId = UUID.randomUUID();

        SecurityUser securityUser = new SecurityUser(
                userId,
                "test@gmail.com",
                "endcoded-password",
                Role.USER,
                false
        );

        // 인증 객체의 암호문을 제거한다 DB 정보는 변경하지 않는다.
        securityUser.eraseCredentials();

        assertThat(securityUser.getPassword()).isNull();
        assertThat(securityUser.getUserId()).isEqualTo(userId);
    }
}