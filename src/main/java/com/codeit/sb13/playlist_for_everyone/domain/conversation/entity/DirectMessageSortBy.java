package com.codeit.sb13.playlist_for_everyone.domain.conversation.entity;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.Arrays;

/** DM 목록의 정렬 기준이다. 외부 요청·응답에는 enum 상수명이 아닌 createdAt을 사용한다. */
@Getter
@RequiredArgsConstructor
public enum DirectMessageSortBy {
    CREATED_AT("createdAt");

    private final String sortKey;

    /** 외부 정렬 키를 정확히 일치하는 값으로 변환하며, 지원하지 않는 값은 거절한다. */
    public static DirectMessageSortBy from(String value){
        return Arrays.stream(DirectMessageSortBy.values())
                .filter(sortBy -> sortBy.sortKey.equals(value))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("지원하지 않는 DM(메시지) 정렬 기준입니다. sort key: " + value));
    }
}
