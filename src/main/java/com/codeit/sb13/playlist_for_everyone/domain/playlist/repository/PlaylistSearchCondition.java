package com.codeit.sb13.playlist_for_everyone.domain.playlist.repository;

import com.codeit.sb13.playlist_for_everyone.domain.playlist.entity.PlaylistSortBy;
import com.codeit.sb13.playlist_for_everyone.global.domain.SortDirection;
import java.util.UUID;
import org.springframework.util.StringUtils;

public record PlaylistSearchCondition(
    String keywordLike,
    UUID ownerIdEqual,
    UUID subscriberIdEqual,
    String cursor,
    UUID idAfter,
    int limit,
    PlaylistSortBy sortBy,
    SortDirection sortDirection
) {

  public boolean hasCursor() {
    return StringUtils.hasText(cursor) && idAfter != null;
  }

  public boolean isDesc() {
    return sortDirection == SortDirection.DESCENDING;
  }
}
