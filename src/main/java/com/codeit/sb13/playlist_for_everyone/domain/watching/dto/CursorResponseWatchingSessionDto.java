package com.codeit.sb13.playlist_for_everyone.domain.watching.dto;

import jakarta.annotation.Nullable;

import java.util.List;
import java.util.UUID;

public record CursorResponseWatchingSessionDto<T> (
    List<T> data,
    @Nullable String nextCursor,
    @Nullable UUID nextIdAfter,
    boolean hasNext,
    long totalCount,
    String sortBy,
    String sortDirection
){ }
