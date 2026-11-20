package com.codeit.sb13.playlist_for_everyone.domain.auth.entity;

import com.codeit.sb13.playlist_for_everyone.domain.user.entity.User;
import com.codeit.sb13.playlist_for_everyone.global.domain.CreatedAtEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 사용자와 소셜 제공자 계정의 연결 정보
 *
 * 같은 사용자의 같은 회원 ID는 한 사용자에게만 연결되고,
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
        throw new UnsupportedOperationException("아직 구현되지 않았습니다.");
    }


}
