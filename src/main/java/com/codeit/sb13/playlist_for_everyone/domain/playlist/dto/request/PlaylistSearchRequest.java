package com.codeit.sb13.playlist_for_everyone.domain.playlist.dto.request;

import com.codeit.sb13.playlist_for_everyone.domain.playlist.entity.PlaylistSortBy;
import com.codeit.sb13.playlist_for_everyone.global.domain.SortDirection;
import java.util.UUID;

public record PlaylistSearchRequest(
    String keywordLike,
    UUID ownerIdEqual,
    UUID subscriberIdEqual,
    String cursor,
    UUID idAfter,
    Integer limit,
    PlaylistSortBy sortBy,
    SortDirection direction
) {

}
