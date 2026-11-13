package com.codeit.sb13.playlist_for_everyone.domain.playlist.service.impl;

import com.codeit.sb13.playlist_for_everyone.domain.content.entity.ContentType;
import java.util.List;
import java.util.UUID;

/** 조회 트랜잭션 안에서 만든 콘텐츠 데이터. 썸네일 URL은 트랜잭션 밖에서 fileKey로 만든다. */
public record PlaylistContentData(
    UUID id,
    ContentType type,
    String title,
    String description,
    String thumbnailUrl,
    String fileKey,
    List<String> tags,
    double averageRating,
    int reviewCount
) {
}