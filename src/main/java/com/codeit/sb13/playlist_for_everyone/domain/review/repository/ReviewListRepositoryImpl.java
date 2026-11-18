package com.codeit.sb13.playlist_for_everyone.domain.review.repository;

import com.codeit.sb13.playlist_for_everyone.domain.review.entity.Review;

import java.util.List;
import java.util.UUID;

public class ReviewListRepositoryImpl implements ReviewListRepository {

    @Override
    public List<Review> getReviewsByContentId(UUID contentId) {
        return List.of();
    }

}
