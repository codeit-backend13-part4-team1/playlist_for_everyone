package com.codeit.sb13.playlist_for_everyone.domain.playlist.dto.request;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

public class PlaylistUpdateRequestTest {

  private Validator validator;

  @BeforeEach
  void setUp() {
    validator = Validation.buildDefaultValidatorFactory().getValidator();
  }

  @ParameterizedTest
  @CsvSource(
      value = {
          "새 제목, null",
          "null, 새 설명",
          "새 제목, 새 설명"
      },
      nullValues = "null")
  @DisplayName("title과 description 중 하나 이상이 정상이면 위반이 발생하지 않는다")
  void 하나_이상_정상이면_위반이_발생하지_않는다(String title, String description) {
    // given
    PlaylistUpdateRequest request = new PlaylistUpdateRequest(title, description);

    // when
    Set<ConstraintViolation<PlaylistUpdateRequest>> violations = validator.validate(request);

    // then
    assertThat(violations).isEmpty();
  }

  @Test
  @DisplayName("title과 description 둘다 null이면 위반이다 ")
  void 인자_둘다_null이면_위반이_발생한다() {
    // given
    PlaylistUpdateRequest request = new PlaylistUpdateRequest(null, null);

    // when
    Set<ConstraintViolation<PlaylistUpdateRequest>> violations = validator.validate(request);

    // then
    assertThat(violations).hasSize(1);
    assertThat(violations)
        .extracting(
            ve -> ve.getPropertyPath().toString())
        .containsExactly("updatable");
  }

  @ParameterizedTest
  @ValueSource(strings = {"", " "})
  @DisplayName("title이 비어 있거나 공백이면 위반이 발생한다")
  void title이_공백이면_위반이_발생한다(String title) {
    // given
    PlaylistUpdateRequest request = new PlaylistUpdateRequest(title, null);

    // when
    Set<ConstraintViolation<PlaylistUpdateRequest>> violations = validator.validate(request);

    // then
    assertThat(violations)
        .extracting(ve -> ve.getPropertyPath().toString())
        .containsExactly("titleNotBlank");
  }

  @ParameterizedTest
  @ValueSource(strings = {"", " "})
  @DisplayName("description이 비어 있거나 공백이면 위반이 발생한다")
  void description이_공백이면_위반이_발생한다(String description) {
    // given
    PlaylistUpdateRequest request = new PlaylistUpdateRequest(null, description);

    // when
    Set<ConstraintViolation<PlaylistUpdateRequest>> violations = validator.validate(request);

    // then
    assertThat(violations)
        .extracting(ve -> ve.getPropertyPath().toString())
        .containsExactly("descriptionNotBlank");
  }

  @Test
  @DisplayName("title이 100자이면 위반이 발생하지 않는다")
  void title이_100자이면_위반이_발생하지_않는다() {
    // given
    PlaylistUpdateRequest request = new PlaylistUpdateRequest("a".repeat(100), null);

    // when
    Set<ConstraintViolation<PlaylistUpdateRequest>> violations = validator.validate(request);

    // then
    assertThat(violations).isEmpty();
  }

  @Test
  @DisplayName("title이 101자이면 위반이 발생한다")
  void title이_101자이면_위반이_발생한다() {
    // given
    PlaylistUpdateRequest request = new PlaylistUpdateRequest("a".repeat(101), null);

    // when
    Set<ConstraintViolation<PlaylistUpdateRequest>> violations = validator.validate(request);

    // then
    assertThat(violations)
        .extracting(ve -> ve.getPropertyPath().toString())
        .containsExactly("title");
  }

  @Test
  @DisplayName("description이 255자이면 위반이 발생하지 않는다")
  void description이_255자이면_위반이_발생하지_않는다() {
    // given
    PlaylistUpdateRequest request = new PlaylistUpdateRequest(null, "a".repeat(255));

    // when
    Set<ConstraintViolation<PlaylistUpdateRequest>> violations = validator.validate(request);

    // then
    assertThat(violations).isEmpty();
  }

  @Test
  @DisplayName("description이 256자이면 위반이 발생한다")
  void description이_256자이면_위반이_발생한다() {
    // given
    PlaylistUpdateRequest request = new PlaylistUpdateRequest(null, "a".repeat(256));

    // when
    Set<ConstraintViolation<PlaylistUpdateRequest>> violations = validator.validate(request);

    // then
    assertThat(violations)
        .extracting(ve -> ve.getPropertyPath().toString())
        .containsExactly("description");
  }

}
