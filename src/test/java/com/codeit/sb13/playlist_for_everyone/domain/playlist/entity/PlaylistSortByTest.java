package com.codeit.sb13.playlist_for_everyone.domain.playlist.entity;


import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

public class PlaylistSortByTest {

  @ParameterizedTest
  @CsvSource({
      "updatedAt, UPDATED_AT",
      "subscribeCount, SUBSCRIBE_COUNT"
  })
  @DisplayName("입력 값을 이넘으로 변환")
  void 문자열을_이넘으로_정상_변환한다(String sortKey, PlaylistSortBy sortBy) {
    // when
    PlaylistSortBy from = PlaylistSortBy.from(sortKey);

    // then
    assertThat(from).isEqualTo(sortBy);
  }


  @ParameterizedTest
  @ValueSource(strings = {"createdAt","UpdatedAt"})
  @NullAndEmptySource
  @DisplayName("지원하지 않는 값은 이넘으로 변환시 예외를 던진다")
  void 지원하지_않는_값이면_예외가_발생한다(String unsupportedValues) {
    // when & then
    assertThatThrownBy(() -> PlaylistSortBy.from(unsupportedValues))
        .isInstanceOf(IllegalArgumentException.class);


  }


}
