package com.codeit.sb13.playlist_for_everyone.domain.review.repository;

import com.codeit.sb13.playlist_for_everyone.domain.review.entity.Review;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.UUID;

public interface ReviewRepository extends JpaRepository<Review, UUID> {

    @Query("""
            SELECT COALESCE(AVG(r.rating), 0.0)
            FROM Review r
            WHERE r.content.id = :contentId
            """)
    double findAverageRatingByContentId(UUID contentId);

    long countByContentId(UUID contentId);

}
