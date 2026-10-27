package com.codeit.sb13.playlist_for_everyone.domain.playlist.entity;

import java.util.Arrays;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum PlaylistSortBy {
  UPDATED_AT("updatedAt"),
  SUBSCRIBE_COUNT("subscribeCount");

  private final String sortKey;

  public static PlaylistSortBy from(String value) {
   return Arrays.stream(PlaylistSortBy.values())
        .filter(sortBy -> sortBy.sortKey.equals(value))
        .findFirst()
        .orElseThrow(() -> new IllegalArgumentException("지원하지 않는 플레이리스 정렬 기준입니다. sort key: " + value));
  }
}