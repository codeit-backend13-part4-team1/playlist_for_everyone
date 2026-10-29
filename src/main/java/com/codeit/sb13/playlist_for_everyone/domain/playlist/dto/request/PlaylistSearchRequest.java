package com.codeit.sb13.playlist_for_everyone.domain.playlist.dto.request;

import com.codeit.sb13.playlist_for_everyone.domain.playlist.entity.PlaylistSortBy;
import com.codeit.sb13.playlist_for_everyone.global.domain.SortDirection;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.time.LocalDateTime;
import java.util.UUID;
import org.springframework.util.StringUtils;
import java.time.format.DateTimeParseException;

public record PlaylistSearchRequest(
    String keywordLike,
    UUID ownerIdEqual,
    UUID subscriberIdEqual,
    String cursor,
    UUID idAfter,

    @NotNull
    @Positive
    @Max(100)
    Integer limit,
    @NotNull
    PlaylistSortBy sortBy,
    @NotNull
    SortDirection sortDirection
) {
  @AssertTrue(message = "cursor 형식이 정렬 기준과 맞지 않습니다")
  public boolean isValidCursor() {
    if (!StringUtils.hasText(cursor) || sortBy == null) {
      return true;
    }

    try {
      return switch (sortBy) {
        case UPDATED_AT -> {
          LocalDateTime.parse(cursor);
          yield true;
        }
        case SUBSCRIBE_COUNT -> Long.parseLong(cursor) >= 0;
      };
    } catch (DateTimeParseException | NumberFormatException e) {
      return false;
    }
  }

  @AssertTrue(message = "cursor와 idAfter는 함께 제공해야 합니다")
  public boolean isCursorPairValid() {
    return StringUtils.hasText(cursor) == (idAfter != null);
  }

}
