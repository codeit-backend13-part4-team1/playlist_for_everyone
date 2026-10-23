package com.codeit.sb13.playlist_for_everyone.domain.notification.dto.request;

import com.codeit.sb13.playlist_for_everyone.domain.notification.entity.SortBy;
import com.codeit.sb13.playlist_for_everyone.global.domain.SortDirection;
import java.util.UUID;

public record NotificationSearchRequest(
        String cursor,
        UUID idAfter,
        Integer limit,
        SortDirection sortDirection,
        SortBy sortBy
) {
}
