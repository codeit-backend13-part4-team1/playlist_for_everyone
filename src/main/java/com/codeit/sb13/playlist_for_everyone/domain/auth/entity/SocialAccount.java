package com.codeit.sb13.playlist_for_everyone.domain.auth.entity;

import com.codeit.sb13.playlist_for_everyone.domain.user.entity.User;
import com.codeit.sb13.playlist_for_everyone.global.domain.CreatedAtEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.util.StringUtils;

import java.util.Objects;

/**
 * 사용자와 소셜 제공자 계정의 연결 정보
 *
 * 같은 제공자의 같은 회원 ID는 한 사용자에게만 연결되고,
 * 한 사용자는 제공자마다 하나의 계정만 연결한다.
 */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "social_accounts")
public class SocialAccount extends CreatedAtEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(name = "provider", nullable = false, length = 20)
    private SocialProvider provider;

    // 제공자가 발급한 회원 고유 ID(Google sub, Kakao id)
    @Column(name = "provider_user_id", nullable = false)
    private String providerUserId;

    public static SocialAccount connect(User user, SocialProvider provider, String providerUserId) {
        if (!StringUtils.hasText(providerUserId)) {
            throw new IllegalArgumentException("소셜 제공자 회원 ID는 필수입니다.");
        }
        SocialAccount account = new SocialAccount();
        account.user = Objects.requireNonNull(user, "연결할 사용자는 필수입니다.");
        account.provider = Objects.requireNonNull(provider, "소셜 제공자는 필수입니다.");
        account.providerUserId = providerUserId;
        return account;
    }


}
