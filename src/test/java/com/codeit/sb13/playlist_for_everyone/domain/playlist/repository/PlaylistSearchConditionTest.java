package com.codeit.sb13.playlist_for_everyone.domain.playlist.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.codeit.sb13.playlist_for_everyone.domain.playlist.entity.PlaylistSortBy;
import com.codeit.sb13.playlist_for_everyone.global.domain.SortDirection;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;

class PlaylistSearchConditionTest {

  static Stream<Arguments> cursorCases() {
    UUID idAfter = UUID.randomUUID();
    return Stream.of(
        Arguments.of("2026-10-26T12:00:00", idAfter, true),
        Arguments.of("2026-10-26T12:00:00", null, false),
        Arguments.of(null, idAfter, false),
        Arguments.of("  ", idAfter, false)
    );
  }

  @ParameterizedTest
  @MethodSource("cursorCases")
  @DisplayName("cursor와 idAfter가 모두 있을 때만 커서가 있는 것으로 본다")
  void cursor와_idAfter가_모두_있을_때만_hasCursor가_true이다(
      String cursor, UUID idAfter, boolean expected) {
    // given
    PlaylistSearchCondition condition =
        createCondition(cursor, idAfter, SortDirection.DESCENDING);

    // when
    boolean result = condition.hasCursor();

    // then
    assertThat(result).isEqualTo(expected);
  }

  @ParameterizedTest
  @CsvSource({
      "DESCENDING, true",
      "ASCENDING, false"
  })
  @DisplayName("정렬 방향이 DESCENDING일 때만 isDesc가 true이다")
  void 정렬_방향이_DESCENDING일_때만_isDesc가_true이다(
      SortDirection sortDirection, boolean expected) {
    // given
    PlaylistSearchCondition condition = createCondition(null, null, sortDirection);

    // when
    boolean result = condition.isDesc();

    // then
    assertThat(result).isEqualTo(expected);
  }

  private PlaylistSearchCondition createCondition(
      String cursor, UUID idAfter, SortDirection sortDirection) {
    return new PlaylistSearchCondition(
        null, null, null,
        cursor, idAfter,
        10, PlaylistSortBy.UPDATED_AT, sortDirection);
  }
}