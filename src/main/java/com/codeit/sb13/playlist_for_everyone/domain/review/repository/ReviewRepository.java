package com.codeit.sb13.playlist_for_everyone.domain.review.repository;

import com.codeit.sb13.playlist_for_everyone.domain.content.repository.ContentRatingProjection;
import com.codeit.sb13.playlist_for_everyone.domain.review.entity.Review;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface ReviewRepository extends JpaRepository<Review, UUID> {

    @Query("""
            SELECT COALESCE(AVG(r.rating), 0.0)
            FROM Review r
            WHERE r.content.id = :contentId
            """)
    double findAverageRatingByContentId(UUID contentId);

    /** 플레이리스트 리뷰 일괄 조회 */
    @Query("""
        SELECT
            r.content.id AS contentId,
            AVG(r.rating) AS averageRating,
            COUNT(r.id) AS reviewCount
        FROM Review r
        WHERE r.content.id IN :contentIds
        GROUP BY r.content.id
        """)
    List<ContentRatingProjection> findRatingsByContentIds(
            @Param("contentIds") Collection<UUID> contentIds
    );

    long countByContentId(UUID contentId);

    @Modifying(flushAutomatically = true)
    @Query("DELETE FROM Review r WHERE r.content.id = :contentId")
    int deleteAllByContentId(UUID contentId);

}
