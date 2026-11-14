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

/**
 * DM 목록 GET의 쿼리 파라미터와 Bean Validation 규칙을 정의한다.
 *
 * <p>limit은 양수이며 limit·sortDirection·sortBy는 필수다. 기본값이나 최대 페이지 크기를 지정하지 않는다.
 * 정렬값은 바인딩 과정에서 변환하고, 커서의 ISO LocalDateTime 형식과 cursor·idAfter 조합은 별도로 검증한다.
 * 대화방 ID와 요청자 ID는 쿼리 파라미터가 아니라 컨트롤러가 경로와 인증 정보에서 가져온다.
 */
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

        /** 요청 검증을 마친 값에 경로의 대화방 ID와 인증된 요청자 ID를 합친다. 양수·커서 검증을 반복하지 않는다. */
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
