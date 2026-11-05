package com.codeit.sb13.playlist_for_everyone.domain.notification.dto.request;

import com.codeit.sb13.playlist_for_everyone.domain.notification.dto.repository.NotificationSearchCondition;
import com.codeit.sb13.playlist_for_everyone.domain.notification.entity.SortBy;
import com.codeit.sb13.playlist_for_everyone.global.domain.SortDirection;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.UUID;

/**
 * 클라이언트가 지정하는 페이지 위치와 정렬 조건이다. 수신자 ID는 입력받지 않는다.
 *
 * <p>limit은 양수이고 정렬 기준·방향은 필수다. cursor가 전달되면 idAfter 유무와 관계없이
 * LocalDateTime 형식을 검증한다. 다음 페이지 조건은 cursor와 idAfter가 모두 있을 때 적용하며,
 * 둘 중 하나만 전달한 요청을 거부하는 쌍 검증은 현재 수행하지 않는다.
 */
public record NotificationSearchRequest(
        String cursor,
        UUID idAfter,
        @NotNull
        @Positive
        Integer limit,
        @NotNull
        SortDirection sortDirection,
        @NotNull
        SortBy sortBy
) {

    @AssertTrue(message = "cursor는 올바른 날짜와 시간 형식이어야 합니다.")
    public boolean isValidCursor(){
        if (cursor == null ){
            return true;
        }

        try {
            LocalDateTime.parse(cursor);
            return true;
        } catch (DateTimeParseException e){
            return false;
        }
    }

    /** 컨트롤러가 인증 정보에서 추출한 수신자 ID를 더해 내부 조회 조건을 만든다. */
    public NotificationSearchCondition toCondition(UUID receiverId){
        return new NotificationSearchCondition(cursor, idAfter, limit, sortDirection, sortBy, receiverId);
    }
}
