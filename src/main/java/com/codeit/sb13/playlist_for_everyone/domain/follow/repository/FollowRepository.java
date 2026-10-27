package com.codeit.sb13.playlist_for_everyone.domain.follow.repository;

import com.codeit.sb13.playlist_for_everyone.domain.follow.entity.Follow;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface FollowRepository extends JpaRepository<Follow, UUID> {

    @Query("""
        SELECT f
        FROM Follow f
        WHERE f.followee.id = :followeeId
            AND f.follower.id = :followerId
    """)
    Optional<Follow> findByFolloweeIdAndFollowerId(UUID followeeId, UUID followerId);

    @Query("""
        SELECT COUNT(f.id)
        FROM Follow f
        WHERE f.followee.id = :followeeId
    """)
    Long countByFolloweeId(@Param("followeeId") UUID followeeId);

    @Query("""
        SELECT f.follower.id
        FROM Follow f
        WHERE f.followee.id = :followeeId
    """
    )
    List<UUID> findFollowerIdsByFolloweeId(@Param("followeeId") UUID followeeId);
}
