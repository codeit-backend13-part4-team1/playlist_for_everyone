package com.codeit.sb13.playlist_for_everyone.domain.review.repository;

import com.codeit.sb13.playlist_for_everyone.domain.review.dto.condition.ReviewListCondition;
import com.codeit.sb13.playlist_for_everyone.domain.review.entity.Review;

import java.util.List;
import java.util.UUID;

public interface ReviewListRepository {

    List<Review> getReviewsByContentId(UUID contentId, ReviewListCondition condition);

}
