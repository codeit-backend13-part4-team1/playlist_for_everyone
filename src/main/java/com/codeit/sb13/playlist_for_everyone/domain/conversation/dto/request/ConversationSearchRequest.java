package com.codeit.sb13.playlist_for_everyone.domain.conversation.dto.request;

import com.codeit.sb13.playlist_for_everyone.domain.conversation.dto.repository.ConversationSearchCondition;
import com.codeit.sb13.playlist_for_everyone.domain.conversation.entity.ConversationSortBy;
import com.codeit.sb13.playlist_for_everyone.global.domain.SortDirection;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.time.DateTimeException;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 목록 조회의 쿼리 파라미터와 HTTP 입력 검증을 담당한다.
 *
 * <p>limit·정렬 기준·방향은 기본값 없이 필수로 받으며, limit은 양수여야 한다.
 * 첫 페이지는 cursor·idAfter를 모두 생략하고, 다음 페이지는 두 값을 함께 전달한다.
 * 요청자 ID는 클라이언트 입력에 포함하지 않고 인증 정보를 전달받아 내부 조회 조건에 담는다.
 */
public record ConversationSearchRequest(
        String keywordLike,
        String cursor,
        UUID idAfter,
        @NotNull @Positive
        Integer limit,
        @NotNull
        SortDirection sortDirection,
        @NotNull
        ConversationSortBy sortBy
) {

        /**
         * 생략한 커서는 허용하고, 전달한 값은 시간대 없는 ISO LocalDateTime으로 해석 가능한지 검사한다.
         * 빈 문자열은 생략으로 보지 않으며, idAfter와의 동시 전달 여부는 별도 검증에서 확인한다.
         */
        @AssertTrue(message = "cursor는 올바른 날짜와 시간 형식이어야 합니다.")
        public boolean isValidCursor(){
                if (cursor == null) {
                        return true;
                }

                try {
                        LocalDateTime.parse(cursor);
                        return true;
                } catch (DateTimeException e) {
                        return false;
                }
        }

        /** 두 값이 모두 없거나 모두 있어야 한다. 값의 형식이 올바른지와는 별개의 검증이다. */
        @AssertTrue(message = "cursor와 idAfter는 함께 전달해야 합니다.")
        public boolean isValidCursorPair(){
                boolean cursorPresent = cursor != null;
                boolean idAfterPresent = idAfter != null;

                return cursorPresent == idAfterPresent;
        }

        public ConversationSearchCondition toCondition(UUID requesterId){
                return new ConversationSearchCondition(
                        keywordLike,
                        cursor,
                        idAfter,
                        limit,
                        sortDirection,
                        sortBy,
                        requesterId
                );
        }
}
