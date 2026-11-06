package com.codeit.sb13.playlist_for_everyone.domain.content.repository;

import com.codeit.sb13.playlist_for_everyone.domain.content.entity.Content;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface ContentRepository extends JpaRepository<Content, UUID> {

    @Query("""
        SELECT
            c AS content,
            COALESCE(rs.averageRating, 0.0) AS averageRating,
            COALESCE(rs.reviewCount, 0L) AS reviewCount
        FROM Content c
        LEFT JOIN (
            SELECT
                r.content.id AS contentId,
                AVG(r.rating) AS averageRating,
                COUNT(r.id) AS reviewCount
            FROM Review r
            WHERE r.content.id = :contentId
            GROUP BY r.content.id
        ) rs ON rs.contentId = c.id
        WHERE c.id = :contentId
        """)
    Optional<ContentDetailProjection> findDetailById(
            @Param("contentId") UUID contentId
    );

}
