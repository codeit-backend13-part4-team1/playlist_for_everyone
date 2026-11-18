package com.codeit.sb13.playlist_for_everyone.domain.notification.dto.repository;

import com.codeit.sb13.playlist_for_everyone.domain.notification.entity.SortBy;
import com.codeit.sb13.playlist_for_everyone.global.domain.SortDirection;
import org.springframework.util.StringUtils;

import java.util.Objects;
import java.util.UUID;

/**
 * 페이지 조건과 조회 대상 수신자를 함께 전달하는 내부 계약이다.
 *
 * <p>호출자는 검증된 조회 값과 수신자 ID를 제공해야 한다. 생성자의 null 검사만으로
 * HTTP 요청 검증이나 사용자 인증을 대신하지 않는다. 목록과 전체 개수 조회가 같은 수신자를 사용한다.
 */
public record NotificationSearchCondition(
        String cursor,
        UUID idAfter,
        Integer limit,
        SortDirection sortDirection,
        SortBy sortBy,
        UUID receiverId
) {
    public NotificationSearchCondition{
        Objects.requireNonNull(limit);
        Objects.requireNonNull(sortBy);
        Objects.requireNonNull(sortDirection);
    }

    public boolean isDesc(){
        return sortDirection.equals(SortDirection.DESCENDING);
    }

    /** 시각과 ID가 모두 있을 때만 페이지 경계를 적용하고, 불완전한 쌍은 첫 페이지로 처리한다. */
    public boolean isCursorAndIdPresent(){
        return StringUtils.hasText(cursor) && idAfter != null;
    }


}
