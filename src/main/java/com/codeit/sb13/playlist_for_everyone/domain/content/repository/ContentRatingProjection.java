package com.codeit.sb13.playlist_for_everyone.domain.content.repository;

import java.util.UUID;

public interface ContentRatingProjection {

    UUID getContentId();

    Double getAverageRating();

    Long getReviewCount();

}
