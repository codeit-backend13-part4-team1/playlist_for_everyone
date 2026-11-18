package com.codeit.sb13.playlist_for_everyone.domain.user.entity;

import com.codeit.sb13.playlist_for_everyone.global.domain.UpdatedAtEntity;
import com.codeit.sb13.playlist_for_everyone.global.file.entity.FileInfo;
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

    // 이미지 없이 가입할 수 있으며, 한 파일을 여러 사용자의 프로필로 공유하지 않는다.
    @OneToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(
            name = "profile_image_id",
            unique = true,
            foreignKey = @ForeignKey(name = "fk_users_profile_image")
    )
    private FileInfo profileImage;


    @Builder
    private User(String email, String name, String password, Role role) {
        this.email = Objects.requireNonNull(email, "이메일은 필수입니다.");
        this.name = name;
        this.password = password;
        this.role = Objects.requireNonNull(role, "role은 필수입니다.");
        this.enabled = true;
    }

    // 지정된 파일 정보를 프로필 이미지로 연결한다.
    public void updateProfileImage(FileInfo profileImage) {
        this.profileImage = Objects.requireNonNull(profileImage, "프로필 이미지 정보는 필수입니다.");
    }

    /**
     * 프로필 이미지 연결만 해제한다.
     * 파일 정보와 실제 저장 파일을 삭제하는 메서드는 아니다.
     */
    public void removeProfileImage() {
        this.profileImage = null;
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
