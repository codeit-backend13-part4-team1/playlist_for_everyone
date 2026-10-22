package com.codeit.sb13.playlist_for_everyone.global.dto.response;

import com.codeit.sb13.playlist_for_everyone.global.domain.SortDirection;

import java.util.List;
import java.util.UUID;

public record CursorResponse<T>(
    List<T> data,
    String nextCursor,
    UUID nextIdAfter,
    boolean hasNext,
    long totalCount,
    String sortBy,
    SortDirection sortDirection
) {

}
