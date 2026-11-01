package com.codeit.sb13.playlist_for_everyone.domain.auth.security;


import com.codeit.sb13.playlist_for_everyone.domain.user.entity.Role;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;

import java.util.List;
import java.util.UUID;

/**
 * Spring Security에서 사용하는 인증 사용자 정보
 *
 * 기본 UserDetails 구현체를 상속하고 서비스이 사용자 ID와 역할을 추가한다.
 * 인증 완료 후 Controller에서 @AuthenticationPrincipal로 받을 수 있다.
 */
public class SecurityUser extends User {

    private final UUID userId;

    private final Role role;

    /**
     *
     * @param userId 사용자 ID
     * @param email 로그인 식별자로 사용할 이메일
     * @param encodedPassword DB에 저장된 암호화된 비밀번호
     * @param role 사용자 역할
     * @param locked 계정 잠금 여부. true이면 로그인 불가
     */
    public SecurityUser(UUID userId, String email, String encodedPassword, Role role, boolean locked) {
        super(
                email,
                encodedPassword,
                !locked, // enabled: 잠기지 않은 계정만 활성 상태
                true, //  accountNonExpired: 계정 만료 정책 아직 없음
                true, // credentialsNonExpired: 비밀번호 만료 정책 아직 없음
                !locked, // accountNonLocked: 계정 잠김 여부의 반대 값
                // hasRole("ADMIN") 등과 연동하도록 ROLE_ 점두사 사용
                List.of(new SimpleGrantedAuthority("ROLE_" + role.name()))
                );

        this.userId = userId;
        this.role = role;
    }

    public UUID getUserId() {
        return userId;
    }

    public Role getRole() {
        return role;
    }


}
