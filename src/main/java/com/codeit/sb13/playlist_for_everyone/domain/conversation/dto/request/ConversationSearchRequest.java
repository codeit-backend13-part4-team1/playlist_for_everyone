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
