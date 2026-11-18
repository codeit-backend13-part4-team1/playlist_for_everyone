package com.codeit.sb13.playlist_for_everyone.domain.notification.entity;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.Arrays;

@Getter
@RequiredArgsConstructor
public enum SortBy {
    CREATED_AT("createdAt");

    private final String sortKey;

    public static SortBy from(String value){
        return Arrays.stream(SortBy.values())
                .filter(sortBy -> sortBy.sortKey.equals(value))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("지원하지 않는 알림 정렬 기준입니다. sort key: " + value));
    }
}
