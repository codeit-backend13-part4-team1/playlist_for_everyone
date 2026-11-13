package com.codeit.sb13.playlist_for_everyone.domain.conversation.entity;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.Arrays;

@Getter
@RequiredArgsConstructor
public enum ConversationSortBy {
    CREATED_AT("createdAt");

    private final String sortKey;

    public static ConversationSortBy from(String value){
        return Arrays.stream(ConversationSortBy.values())
                .filter(sortBy -> sortBy.sortKey.equals(value))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("지원하지 않는 대화방 정렬 기준입니다. sort key: " + value));
    }
}
