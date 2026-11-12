package com.codeit.sb13.playlist_for_everyone.domain.auth.entity;

import com.codeit.sb13.playlist_for_everyone.global.domain.UpdatedAtEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "temporary_passwords")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TemporaryPassword extends UpdatedAtEntity {

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "password", nullable = false)
    private String password;

    @Column(name = "expired_at", nullable = false)
    private LocalDateTime expiredAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 10)
    private TemporaryPasswordStatus status;


    private TemporaryPassword(UUID userId, String encodedPassword, LocalDateTime expiredAt) {
        this.userId = Objects.requireNonNull(userId, "사용자 ID는 필수입니다.");
        this.password = Objects.requireNonNull(encodedPassword, "임시 비밀번호는 필수입니다.");
        this.expiredAt = Objects.requireNonNull(expiredAt, "만료 시각은 필수입니다.");
        this.status = TemporaryPasswordStatus.ACTIVE;
    }

    public static TemporaryPassword issue(UUID userId, String encodedPassword, LocalDateTime expiredAt) {
        return new TemporaryPassword(userId, encodedPassword, expiredAt);
    }


    /**
     * 파기되지 않았고 만료 시각 전이면 사용할 수 있다.
     * 만료 시각과 같은 순간부터는 사용할 수 없다.
     */
    public boolean isUsableAt(LocalDateTime now) {
        return status == TemporaryPasswordStatus.ACTIVE && now.isBefore(expiredAt);
    }

    /**
     * 재발급하거나 비밀번호를 변경하면 기존 임시 비밀번호를 파기한다.
     */
    public void discard() {
        this.status = TemporaryPasswordStatus.DISCARDED;
    }

}
