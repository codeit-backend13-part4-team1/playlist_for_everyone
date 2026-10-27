package com.codeit.sb13.playlist_for_everyone.domain.playlist.entity;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum PlaylistSortBy {
  UPDATED_AT("updatedAt"),
  SUBSCRIBE_COUNT("subscribeCount");

  private final String sortKey;

  public static PlaylistSortBy from(String value) {
    return null;   // 구현 전 빈 틀
  }
}