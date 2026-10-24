package com.codeit.sb13.playlist_for_everyone.domain.follow.entity;

import com.codeit.sb13.playlist_for_everyone.domain.user.entity.User;
import com.codeit.sb13.playlist_for_everyone.global.domain.CreatedAtEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Entity
@Table(
        name = "follows",
        uniqueConstraints = {
                @UniqueConstraint(name = "uq_follows_follower_followee", columnNames = {"follower_id", "followee_id"})
        })
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Getter
public class Follow extends CreatedAtEntity {

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "follower_id", nullable = false)
    private User follower;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "followee_id", nullable = false)
    private User followee;

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
}
