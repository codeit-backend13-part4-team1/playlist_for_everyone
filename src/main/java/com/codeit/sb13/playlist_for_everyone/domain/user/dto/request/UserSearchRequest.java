package com.codeit.sb13.playlist_for_everyone.domain.user.dto.request;

import com.codeit.sb13.playlist_for_everyone.domain.user.dto.UserSearchCondition;
import com.codeit.sb13.playlist_for_everyone.domain.user.entity.Role;
import com.codeit.sb13.playlist_for_everyone.domain.user.entity.UserSortBy;
import com.codeit.sb13.playlist_for_everyone.global.domain.SortDirection;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.Arrays;
import java.util.UUID;

public record UserSearchRequest(
        String emailLike,
        Role roleEqual,
        Boolean isLocked,
        String cursor,
        UUID idAfter,

        @NotNull
        @Positive
        @Max(100)
        Integer limit,

        @NotNull
        SortDirection sortDirection,

        @NotNull
        UserSortBy sortBy
) {

    /**
     * cursor는 "마지막 항목의 정렬 기준 값"이므로 sortBy에 따라 형식이 다르다.
     * 첫 페이지(idAfter 없음)는 검사하지 않는다.
     * 이름이 없는 사용자의 cursor는 빈 문자열이므로 name·email은 빈 값도 허용한다.
     */
    @AssertTrue(message = "cursor 형식이 정렬 기준과 맞지 않습니다")
    public boolean isValidCursor() {
        if (idAfter == null || cursor == null || sortBy == null) {
            return true;
        }

        return switch (sortBy) {
            case NAME, EMAIL -> true;
            case CREATED_AT -> isDateTime(cursor);
            case IS_LOCKED -> "true".equals(cursor) || "false".equals(cursor);
            case ROLE -> isRole(cursor);
        };
    }

    /**
     * 다음 페이지인지는 idAfter로 판단한다.
     * idAfter가 있으면 cursor가 있어야 하고(빈 문자열 포함), 없으면 cursor도 비어 있어야 한다.
     */
    @AssertTrue(message = "cursor와 idAfter는 함께 제공해야 합니다")
    public boolean isCursorPairValid() {
        if (idAfter != null) {
            return cursor != null;
        }
        return !StringUtils.hasText(cursor);
    }

    private static boolean isDateTime(String value) {
        try {
            LocalDateTime.parse(value);
            return true;
        } catch (DateTimeParseException e) {
            return false;
        }
    }

    private static boolean isRole(String value) {
        return Arrays.stream(Role.values())
                .anyMatch(role -> role.name().equals(value));
    }

    public UserSearchCondition toCondition() {
        return new UserSearchCondition(
                emailLike, roleEqual, isLocked, cursor, idAfter,
                limit, sortDirection, sortBy
        );
    }

}
