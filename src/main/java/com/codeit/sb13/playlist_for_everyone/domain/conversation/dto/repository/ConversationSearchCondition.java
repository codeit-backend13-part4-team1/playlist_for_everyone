package com.codeit.sb13.playlist_for_everyone.domain.conversation.dto.repository;

import com.codeit.sb13.playlist_for_everyone.domain.conversation.entity.ConversationSortBy;
import com.codeit.sb13.playlist_for_everyone.global.domain.SortDirection;
import org.springframework.util.StringUtils;

import java.util.Objects;
import java.util.UUID;

public record ConversationSearchCondition (
        String keywordLike,
        String cursor,
        UUID idAfter,
        Integer limit,
        SortDirection sortDirection,
        ConversationSortBy sortBy,
        UUID requesterId
){

    public ConversationSearchCondition{
        Objects.requireNonNull(limit);
        Objects.requireNonNull(sortBy);
        Objects.requireNonNull(sortDirection);
        Objects.requireNonNull(requesterId);
    }

    public boolean isDesc(){
        return sortDirection.equals(SortDirection.DESCENDING);
    }

    public boolean isCursorAndIdPresent(){
        return StringUtils.hasText(cursor) && idAfter != null;
    }
}
