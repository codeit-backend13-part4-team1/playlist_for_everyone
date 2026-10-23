package com.codeit.sb13.playlist_for_everyone.domain.notification.dto.repository;

import com.codeit.sb13.playlist_for_everyone.domain.notification.entity.SortBy;
import com.codeit.sb13.playlist_for_everyone.global.domain.SortDirection;

import java.util.Objects;
import java.util.UUID;

public record NotificationSearchCondition(
        String cursor,
        UUID idAfter,
        Integer limit,
        SortDirection sortDirection,
        SortBy sortBy,
        UUID receiverId
) {
    public NotificationSearchCondition{
        Objects.requireNonNull(limit);
        Objects.requireNonNull(sortBy);
        Objects.requireNonNull(sortDirection);
    }

}
