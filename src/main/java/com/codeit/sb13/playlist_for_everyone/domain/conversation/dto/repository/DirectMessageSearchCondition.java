package com.codeit.sb13.playlist_for_everyone.domain.conversation.dto.repository;

import com.codeit.sb13.playlist_for_everyone.domain.conversation.entity.DirectMessageSortBy;
import com.codeit.sb13.playlist_for_everyone.global.domain.SortDirection;
import org.springframework.util.StringUtils;

import java.util.Objects;
import java.util.UUID;

/**
 * DM 조회 계층에 대화방·요청자·정렬·커서 조건을 전달한다.
 *
 * <p>생성자는 필수 값의 null 여부만 검사한다. 양수 limit, 커서 형식·조합 등 요청 검증 전체를
 * 대신하지 않으므로 호출자는 검증을 마친 값을 전달해야 한다. 커서는 생성 시각과 메시지 ID의 쌍이며,
 * 기준 메시지를 다시 조회하지 않고 전달된 값 자체로 조회 범위를 결정한다.
 */
public record DirectMessageSearchCondition(
        UUID conversationId,
        String cursor,
        UUID idAfter,
        Integer limit,
        SortDirection sortDirection,
        DirectMessageSortBy sortBy,
        UUID requesterId
){

    public DirectMessageSearchCondition {
        Objects.requireNonNull(conversationId);
        Objects.requireNonNull(limit);
        Objects.requireNonNull(sortBy);
        Objects.requireNonNull(sortDirection);
        Objects.requireNonNull(requesterId);
    }

    public boolean isDesc(){
        return sortDirection.equals(SortDirection.DESCENDING);
    }

    /** 커서 조건을 적용할 값이 있는지 판단한다. 커서 형식이나 잘못된 조합을 검증하는 메서드는 아니다. */
    public boolean isCursorAndIdPresent(){
        return StringUtils.hasText(cursor) && idAfter != null;
    }
}
