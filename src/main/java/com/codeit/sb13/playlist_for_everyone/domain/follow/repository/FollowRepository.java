package com.codeit.sb13.playlist_for_everyone.domain.follow.repository;

import com.codeit.sb13.playlist_for_everyone.domain.follow.entity.Follow;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * 방향 있는 팔로우 관계를 조회·저장하는 JPA 저장소.
 * 사용자 존재 확인과 자기 팔로우 정책은 상위 계층이 담당한다.
 */
public interface FollowRepository extends JpaRepository<Follow, UUID> {

    /** 대상과 요청자 ID를 모두 사용해 해당 요청자의 관계만 조회한다. */
    @Query("""
        SELECT f
        FROM Follow f
        WHERE f.followee.id = :followeeId
            AND f.follower.id = :followerId
    """)
    Optional<Follow> findByFolloweeIdAndFollowerId(UUID followeeId, UUID followerId);

    /** 사용자 테이블을 별도 조회하지 않으므로 존재하지 않는 대상도 관계 수는 0이다. */
    @Query("""
        SELECT COUNT(f.id)
        FROM Follow f
        WHERE f.followee.id = :followeeId
    """)
    Long countByFolloweeId(@Param("followeeId") UUID followeeId);

    /** 후속 도메인이 수신자 등을 찾을 때 사용할 ID 목록. 정렬·페이징 계약은 없다. */
    @Query("""
        SELECT f.follower.id
        FROM Follow f
        WHERE f.followee.id = :followeeId
    """
    )
    List<UUID> findFollowerIdsByFolloweeId(@Param("followeeId") UUID followeeId);
}
