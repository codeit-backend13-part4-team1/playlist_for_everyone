package com.codeit.sb13.playlist_for_everyone.domain.user.entity;

import com.codeit.sb13.playlist_for_everyone.global.domain.UpdatedAtEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.util.StringUtils;

import java.util.Objects;

@Entity
@Getter
@Table(name = "users",
        uniqueConstraints = @UniqueConstraint(name = "UQ_USERS_EMAIL", columnNames = "email")
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class User extends UpdatedAtEntity {

    @Column(name = "email", nullable = false)
    private String email;

    @Column(name = "nickname", length = 100)
    private String name;

    @Column(name = "password")
    private String password;

    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false, length = 10)
    private Role role;

    // true: 정상사용 가능, false 잠김 (스웨거의 locked = !enabled)
    @Column(name = "enabled", nullable = false)
    private boolean enabled;


    @Builder
    private User(String email, String name, String password, Role role) {
        this.email = Objects.requireNonNull(email, "이메일은 필수입니다.");
        this.name = name;
        this.password = password;
        this.role = Objects.requireNonNull(role, "role은 필수입니다.");
        this.enabled = true;
    }

    public void updateRole(Role role) {
        this.role = Objects.requireNonNull(role, "role은 필수입니다.");
    }

    public void updatePassword(String encodedPassword) {
        this.password = encodedPassword;
    }

    public void updateName(String name) {
        if (!StringUtils.hasText(name)) {
            return;
        }
        this.name = name;
    }

    public void lock() {
        this.enabled = false;
    }

    public void unlock() {
        this.enabled = true;
    }

    public boolean isLocked() {
        return !enabled;
    }

}
