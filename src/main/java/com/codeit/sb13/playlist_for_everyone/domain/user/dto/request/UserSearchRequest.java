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
     * name·email: 아무 문자열 / createdAt: LocalDateTime / isLocked: true·false / role: Role 이름
     */
    @AssertTrue(message = "cursor 형식이 정렬 기준과 맞지 않습니다")
    public boolean isValidCursor() {
        if (!StringUtils.hasText(cursor) || sortBy == null) {
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
     * cursor만 있고 idAfter가 없으면 같은 값이 여러 개일 때 다음 페이지 위치를 정할 수 없다.
     */
    @AssertTrue(message = "cursor와 idAfter는 함께 제공해야 합니다")
    public boolean isCursorPairValid() {
        return StringUtils.hasText(cursor) == (idAfter != null);
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
