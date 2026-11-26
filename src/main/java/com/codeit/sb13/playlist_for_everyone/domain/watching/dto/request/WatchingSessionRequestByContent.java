package com.codeit.sb13.playlist_for_everyone.domain.watching.dto.request;

import java.util.Objects;
import java.util.UUID;

public record WatchingSessionRequestByContent(
        String userName,
        String cursor,
        UUID idAfter,
        int limit,
        String sortDirection,
        String sortBy
) {
    public WatchingSessionRequestByContent {
        try {
            Objects.requireNonNull(userName);
            Objects.requireNonNull(cursor);
            Objects.requireNonNull(idAfter);
        } catch (NullPointerException e) {
            throw new IllegalArgumentException("필수 항목이 누락되었습니다.",e);
        }

        if (!sortDirection.equals("ASCENDING") && !sortDirection.equals("DESCENDING"))
            throw new IllegalArgumentException("조회 방향은 ASCENDING 또는 DESCENDING이어야 합니다.");
        if (!sortBy.equals("createdAt"))
            throw new IllegalArgumentException("정렬 기준은 createdAt이어야 합니다.");
    }
}
