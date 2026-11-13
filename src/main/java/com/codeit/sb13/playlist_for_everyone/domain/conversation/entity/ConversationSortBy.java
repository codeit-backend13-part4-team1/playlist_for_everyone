package com.codeit.sb13.playlist_for_everyone.domain.conversation.entity;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.Arrays;

/**
 * 대화방 목록의 정렬 기준을 정의한다. 현재 지원하는 기준은 대화방 생성 시각뿐이다.
 * 내부 enum 이름은 CREATED_AT이며, 요청 파라미터와 커서 응답에는 sortKey인 createdAt을 사용한다.
 * WebMvcConfig의 변환기가 from을 호출하므로 외부 입력에 enum 이름을 그대로 사용하지 않는다.
 */
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
