package com.codeit.sb13.playlist_for_everyone.domain.user.entity;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 관리자 사용자 목록 정렬 기준.
 * sortKey는 API 쿼리 파라미터 값이며, 응답의 sortBy에도 그대로 내려준다.
 */
@Getter
@RequiredArgsConstructor
public enum UserSortBy {
    NAME("name"),
    EMAIL("email"),
    CREATED_AT("createdAt"),
    IS_LOCKED("isLocked"),
    ROLE("role");

    private final String sortKey;

    public static UserSortBy from(String value) {
        // RED: 다음 단계에서 구현
        throw new UnsupportedOperationException();
    }

}
