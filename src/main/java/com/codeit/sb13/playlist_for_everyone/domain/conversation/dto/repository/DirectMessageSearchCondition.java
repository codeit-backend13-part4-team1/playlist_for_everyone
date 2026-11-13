package com.codeit.sb13.playlist_for_everyone.domain.conversation.dto.repository;

import com.codeit.sb13.playlist_for_everyone.domain.conversation.entity.DirectMessageSortBy;
import com.codeit.sb13.playlist_for_everyone.global.domain.SortDirection;
import org.springframework.util.StringUtils;

import java.util.Objects;
import java.util.UUID;

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
