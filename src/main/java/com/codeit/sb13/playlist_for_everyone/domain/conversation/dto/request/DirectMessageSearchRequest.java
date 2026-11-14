package com.codeit.sb13.playlist_for_everyone.domain.conversation.dto.request;

import com.codeit.sb13.playlist_for_everyone.domain.conversation.dto.repository.DirectMessageSearchCondition;
import com.codeit.sb13.playlist_for_everyone.domain.conversation.entity.DirectMessageSortBy;
import com.codeit.sb13.playlist_for_everyone.global.domain.SortDirection;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.time.DateTimeException;
import java.time.LocalDateTime;
import java.util.UUID;

public record DirectMessageSearchRequest(
        String cursor,
        UUID idAfter,
        @NotNull @Positive
        Integer limit,
        @NotNull
        SortDirection sortDirection,
        @NotNull
        DirectMessageSortBy sortBy
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

        public DirectMessageSearchCondition toCondition(UUID conversationId,UUID requesterId){
                return new DirectMessageSearchCondition(
                        conversationId,
                        cursor,
                        idAfter,
                        limit,
                        sortDirection,
                        sortBy,
                        requesterId
                );
        }

}
