package com.codeit.sb13.playlist_for_everyone.domain.playlist.dto.request;


import static org.assertj.core.api.Assertions.assertThat;

import com.codeit.sb13.playlist_for_everyone.domain.playlist.entity.PlaylistSortBy;
import com.codeit.sb13.playlist_for_everyone.global.domain.SortDirection;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

class PlaylistSearchRequestTest {
  
  private Validator validator;
  
  @BeforeEach
  void setUp() {
    validator = Validation.buildDefaultValidatorFactory().getValidator();
  }

  @ParameterizedTest
  @CsvSource({
      "updatedAt, ASCENDING",
      "updatedAt, DESCENDING",
      "subscribeCount, ASCENDING",
      "subscribeCount, DESCENDING"
  })
  @DisplayName("필수 값만 담은 요청은 위반이 없다")
  void 필수_값만_담은_요청에는_위반이_일어나지_않는다(String sortKey, String sortDirection) {
    // given
    Integer limit = 1;
    PlaylistSortBy sortBy = PlaylistSortBy.from(sortKey);
    SortDirection direction = SortDirection.valueOf(sortDirection);

    PlaylistSearchRequest request = new PlaylistSearchRequest(
        null, null, null, null, null,
        limit, sortBy, direction);

    //when
    Set<ConstraintViolation<PlaylistSearchRequest>> violations = validator.validate(request);

    // then
    assertThat(violations).isEmpty();
  }

  @Test
  @DisplayName("limit이 상한과 같으면 검증을 통과한다")
  void limitEqualToMaxIsValid() {
    PlaylistSearchRequest request =
        new PlaylistSearchRequest(
            null, null, null, null, null, 100, PlaylistSortBy.UPDATED_AT, SortDirection.DESCENDING);

    Set<ConstraintViolation<PlaylistSearchRequest>> violations = validator.validate(request);

    assertThat(violations).isEmpty();
  }

  @Test
  @DisplayName("limit이 상한을 넘으면 검증에 실패한다")
  void limitOverMaxIsInvalid() {
    PlaylistSearchRequest request =
        new PlaylistSearchRequest(
            null, null, null, null, null, 101, PlaylistSortBy.UPDATED_AT, SortDirection.DESCENDING);

    Set<ConstraintViolation<PlaylistSearchRequest>> violations = validator.validate(request);

    assertThat(violations)
        .extracting(v -> v.getPropertyPath().toString())
        .containsExactly("limit");
  }

  @ParameterizedTest
  @NullSource
  @ValueSource(ints = {0, -1})
  @DisplayName("limit이 없거나 0 이하이면 limit에서 위반이 발생한다")
  void limit이_없거나_0이하이면_위반이_발생한다(Integer limit) {
    // given
    PlaylistSearchRequest request = new PlaylistSearchRequest(
        null, null, null, null, null,
        limit, PlaylistSortBy.UPDATED_AT, SortDirection.ASCENDING);

    // when
    Set<ConstraintViolation<PlaylistSearchRequest>> violations = validator.validate(request);

    // then
    assertThat(violations)
        .extracting(ve -> ve.getPropertyPath().toString())
        .containsExactly("limit");
  }

  @Test
  @DisplayName("sortBy가 누락되면 sortBy에서 위반이 발생한다")
  void sortBy가_null이면_위반이_발생한다() {
    // given
    PlaylistSearchRequest request = new PlaylistSearchRequest(
        null, null, null, null, null,
        1, null, SortDirection.ASCENDING);

    // when
    Set<ConstraintViolation<PlaylistSearchRequest>> violations = validator.validate(request);

    // then
    assertThat(violations)
        .extracting(ve -> ve.getPropertyPath().toString())
        .containsExactly("sortBy");
  }

  @Test
  @DisplayName("sortDirection가 누락되면 sortDirection에서 위반이 발생한다.")
  void sortDirection이_null이면_위반이_발생한다() {
    // given
    PlaylistSearchRequest request = new PlaylistSearchRequest(
        null, null, null, null, null,
        1, PlaylistSortBy.UPDATED_AT, null);

    // when
    Set<ConstraintViolation<PlaylistSearchRequest>> violations = validator.validate(request);

    // then
    assertThat(violations)
        .extracting(ve -> ve.getPropertyPath().toString())
        .containsExactly("sortDirection");
  }

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = {" "})
  @DisplayName("cursor가 비어 있으면 위반이 발생하지 않는다")
  void cursor가_비어_있으면_위반이_발생하지_않는다(String cursor) {
    // given
    PlaylistSearchRequest request = new PlaylistSearchRequest(
        null, null, null, cursor, null,
        1, PlaylistSortBy.UPDATED_AT, SortDirection.ASCENDING);

    // when
    Set<ConstraintViolation<PlaylistSearchRequest>> violations = validator.validate(request);

    // then
    assertThat(violations).isEmpty();
  }

  @Test
  @DisplayName("정렬 기준이 updatedAt이고 cursor가 날짜 형식이면 위반이 발생하지 않는다")
  void updatedAt_정렬에서_날짜_형식_cursor는_위반이_발생하지_않는다() {
    // given
    PlaylistSearchRequest request = new PlaylistSearchRequest(
        null, null, null, "2026-10-26T12:00:00", UUID.randomUUID(),
        1, PlaylistSortBy.UPDATED_AT, SortDirection.ASCENDING);

    // when
    Set<ConstraintViolation<PlaylistSearchRequest>> violations = validator.validate(request);

    // then
    assertThat(violations).isEmpty();
  }

  @ParameterizedTest
  @ValueSource(strings = {"Abc", "123"})
  @DisplayName("정렬 기준이 updatedAt이고 cursor가 날짜 형식이 아니면 위반이 발생한다")
  void updatedAt_정렬에서_cursor가_날짜_형식이_아니면_위반이_발생한다(String cursor) {
    // given
    PlaylistSearchRequest request = new PlaylistSearchRequest(
        null, null, null, cursor, UUID.randomUUID(),
        1, PlaylistSortBy.UPDATED_AT, SortDirection.ASCENDING);

    // when
    Set<ConstraintViolation<PlaylistSearchRequest>> violations = validator.validate(request);

    // then
    assertThat(violations)
        .extracting(ve -> ve.getPropertyPath().toString())
        .containsExactly("validCursor");
  }

  @ParameterizedTest
  @ValueSource(strings = {"0", "3"})
  @DisplayName("정렬 기준이 subscribeCount이고 cursor가 0 이상의 숫자이면 위반이 발생하지 않는다")
  void subscribeCount_정렬에서_숫자_cursor는_위반이_발생하지_않는다(String cursor) {
    // given
    PlaylistSearchRequest request = new PlaylistSearchRequest(
        null, null, null, cursor, UUID.randomUUID(),
        1, PlaylistSortBy.SUBSCRIBE_COUNT, SortDirection.ASCENDING);

    // when
    Set<ConstraintViolation<PlaylistSearchRequest>> violations = validator.validate(request);

    // then
    assertThat(violations).isEmpty();
  }

  @ParameterizedTest
  @ValueSource(strings = {"abc", "-1", "2026-10-26T12:00:00"})
  @DisplayName("정렬 기준이 subscribeCount이고 cursor가 0 이상의 숫자가 아니면 위반이 발생한다")
  void subscribeCount_정렬에서_숫자가_아닌_cursor는_위반이_발생한다(String cursor) {
    // given
    PlaylistSearchRequest request = new PlaylistSearchRequest(
        null, null, null, cursor, UUID.randomUUID(),
        1, PlaylistSortBy.SUBSCRIBE_COUNT, SortDirection.ASCENDING);

    // when
    Set<ConstraintViolation<PlaylistSearchRequest>> violations = validator.validate(request);

    // then
    assertThat(violations)
        .extracting(ve -> ve.getPropertyPath().toString())
        .containsExactly("validCursor");
  }

  @Test
  @DisplayName("cursor만 있고 idAfter가 없으면 검증에 실패한다")
  void ID가_없는_커서는_유효하지_않다() {
    PlaylistSearchRequest request =
        new PlaylistSearchRequest(
            null, null, null, "2026-10-27T10:00:00", null, 10,
            PlaylistSortBy.UPDATED_AT, SortDirection.DESCENDING);

    Set<ConstraintViolation<PlaylistSearchRequest>> violations = validator.validate(request);

    assertThat(violations)
        .extracting(v -> v.getPropertyPath().toString())
        .containsExactly("cursorPairValid");
  }

  @Test
  @DisplayName("idAfter만 있고 cursor가 없으면 검증에 실패한다")
  void 커서_없이_ID를_입력하면_유효하지_않다() {
    PlaylistSearchRequest request =
        new PlaylistSearchRequest(
            null, null, null, null, UUID.randomUUID(), 10,
            PlaylistSortBy.UPDATED_AT, SortDirection.DESCENDING);

    Set<ConstraintViolation<PlaylistSearchRequest>> violations = validator.validate(request);

    assertThat(violations)
        .extracting(v -> v.getPropertyPath().toString())
        .containsExactly("cursorPairValid");
  }




}