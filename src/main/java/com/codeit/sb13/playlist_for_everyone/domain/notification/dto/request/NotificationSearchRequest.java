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

}
