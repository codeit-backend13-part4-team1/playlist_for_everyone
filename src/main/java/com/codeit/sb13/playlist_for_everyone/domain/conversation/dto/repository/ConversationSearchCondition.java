package com.codeit.sb13.playlist_for_everyone.domain.conversation.dto.repository;

import com.codeit.sb13.playlist_for_everyone.domain.conversation.entity.ConversationSortBy;
import com.codeit.sb13.playlist_for_everyone.global.domain.SortDirection;
import org.springframework.util.StringUtils;

import java.util.Objects;
import java.util.UUID;

/**
 * 서비스와 Repository가 공유하는 목록 검색·정렬·커서 조건과 인증된 요청자 ID다.
 *
 * <p>생성자는 필수 필드의 null만 방어한다. 양수 limit, 커서 형식과 동시 전달 검증은
 * 요청 DTO에서 완료된다는 전제이며, HTTP 외 호출자도 같은 조건을 만족해야 한다.
 * 검색어 정규화와 커서 비교식 구성은 Repository에서 수행한다.
 */
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

    /** 커서 조건을 적용할 값이 있는지 판단한다. 커서 형식이나 잘못된 조합을 검증하는 메서드는 아니다. */
    public boolean isCursorAndIdPresent(){
        return StringUtils.hasText(cursor) && idAfter != null;
    }
}
