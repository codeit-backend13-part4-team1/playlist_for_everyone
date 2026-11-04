package com.codeit.sb13.playlist_for_everyone.domain.follow.entity;

import com.codeit.sb13.playlist_for_everyone.domain.user.entity.User;
import com.codeit.sb13.playlist_for_everyone.global.domain.CreatedAtEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.UUID;

/**
 * 한 사용자가 다른 사용자를 팔로우하는 방향 있는 관계.
 *
 * <p>A가 B를 팔로우하면 follower는 A, followee는 B다. 같은 방향의 중복은 UNIQUE로 제한하지만
 * A→B와 B→A는 서로 다른 관계다. 실제 DB 제약조건은 Flyway 마이그레이션으로 적용한다.
 * 관계 자체의 ID와 생성 시각은 CreatedAtEntity에서 상속받는다.
 */
@Entity
@Table(
        name = "follows",
        uniqueConstraints = {
                @UniqueConstraint(name = "uq_follows_follower_followee", columnNames = {"follower_id", "followee_id"})
        })
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Getter
public class Follow extends CreatedAtEntity {

    // 사용자는 독립된 도메인이다. LAZY로 참조하며 cascade를 지정하지 않아 관계 삭제를 사용자에게 전파하지 않는다.
    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "follower_id", nullable = false)
    private User follower;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "followee_id", nullable = false)
    private User followee;

    public Follow(User follower, User followee) {
        this.follower = follower;
        this.followee = followee;
    }

    public UUID getFollowerId() {
        if (follower == null) {
            return null;
        }
        return follower.getId();
    }

    public UUID getFolloweeId() {
        if (followee == null) {
            return null;
        }
        return followee.getId();
    }

    /** 지연 로딩이 필요할 수 있으므로 생성 이벤트의 이름은 저장 트랜잭션 안에서 읽는다. */
    public String getFollowerName(){
        if(follower == null){
            return null;
        }
        return follower.getName();
    }
}
