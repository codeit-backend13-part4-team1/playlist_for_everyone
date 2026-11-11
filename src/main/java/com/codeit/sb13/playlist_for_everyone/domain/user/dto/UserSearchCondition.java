package com.codeit.sb13.playlist_for_everyone.domain.user.dto;

import com.codeit.sb13.playlist_for_everyone.domain.user.entity.Role;
import com.codeit.sb13.playlist_for_everyone.domain.user.entity.UserSortBy;
import com.codeit.sb13.playlist_for_everyone.global.domain.SortDirection;

import java.util.UUID;

/**
 * 조회 조건
 * 요청 검증(@Valid)이 끝난 값만 담는다. limit은 검증을 통과했으므로 int.
 */
public record UserSearchCondition(
        String emailLike,
        Role roleEqual,
        Boolean isLocked,
        String cursor,
        UUID idAfter,
        int limit,
        SortDirection sortDirection,
        UserSortBy sortBy
) {
    public boolean hasCursor() {
        return cursor != null && idAfter != null;
    }

    public boolean isDesc() {
        return sortDirection == SortDirection.DESCENDING;
    }

}
