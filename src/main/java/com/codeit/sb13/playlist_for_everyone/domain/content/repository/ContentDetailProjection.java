package com.codeit.sb13.playlist_for_everyone.domain.content.repository;

import com.codeit.sb13.playlist_for_everyone.domain.content.entity.Content;

public interface ContentDetailProjection {

    Content getContent();

    double getAverageRating();

    long getReviewCount();

}
