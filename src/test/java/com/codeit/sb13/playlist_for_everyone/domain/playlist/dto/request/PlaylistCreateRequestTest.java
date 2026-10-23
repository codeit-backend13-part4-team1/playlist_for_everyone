package com.codeit.sb13.playlist_for_everyone.domain.playlist.dto.request;


import static org.assertj.core.api.Assertions.assertThat;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import java.util.Set;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

@DisplayName("playlistRequest")
public class PlaylistCreateRequestTest {

  private static Validator validator;

  @BeforeAll
  static void setUp() {
    validator = Validation.buildDefaultValidatorFactory().getValidator();
  }

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = {"    "})
  @DisplayName("제목이 비어있거나, 공백이면 검증위반")
  void 제목이_비어있거나_공백이면_검증위반이다(String title) {
    // given
    PlaylistCreateRequest request = new PlaylistCreateRequest(title, "설명");

    // when
    Set<ConstraintViolation<PlaylistCreateRequest>> violations = validator.validate(request);

    // then
    assertThat(violations).hasSize(1);
    assertThat(violations.iterator().next().getPropertyPath().toString())
        .isEqualTo("title");


  }

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = {"    "})
  @DisplayName("설명이 비어있거나, 공백이면 검증위반")
  void 설명이_비어있거나_공백이면_검증위반이다(String description) {
    // given
    PlaylistCreateRequest request = new PlaylistCreateRequest("제목", description);

    // when
    Set<ConstraintViolation<PlaylistCreateRequest>> violations = validator.validate(request);

    // then
    assertThat(violations).hasSize(1);
    assertThat(violations.iterator().next().getPropertyPath().toString())
        .isEqualTo("description");


  }

  @Test
  @DisplayName("제목은 100자까지 허용한다.")
  void 제목은_100자까지_허용한다() {
    // given
    String title = "t".repeat(100);

    // when
    PlaylistCreateRequest request = new PlaylistCreateRequest(title, "설명");
    Set<ConstraintViolation<PlaylistCreateRequest>> violations = validator.validate(request);

    // then
    assertThat(violations).isEmpty();
  }

  @Test
  @DisplayName("설명은 255자까지 허용한다.")
  void 설명은_255자까지_허용한다() {
    // given
    String description = "d".repeat(255);

    // when
    PlaylistCreateRequest request = new PlaylistCreateRequest("title", description);
    Set<ConstraintViolation<PlaylistCreateRequest>> violations = validator.validate(request);

    // then
    assertThat(violations).isEmpty();
  }

  @Test
  @DisplayName("제목은 100자를 초과할 수 없다.")
  void 제목은_100자를_초과할_수_없다() {
    // given
    String title = "t".repeat(101);

    // when
    PlaylistCreateRequest request = new PlaylistCreateRequest(title, "description");
    Set<ConstraintViolation<PlaylistCreateRequest>> violations = validator.validate(request);

    // then
    assertThat(violations).hasSize(1);
    assertThat(violations.iterator().next().getPropertyPath().toString())
        .isEqualTo("title");
  }

  @Test
  @DisplayName("설명은 255자를 초과할 수 없다.")
  void 설명은_255자를_초과할_수_없다() {
    // given
    String description = "d".repeat(256);

    // when
    PlaylistCreateRequest request = new PlaylistCreateRequest("title", description);
    Set<ConstraintViolation<PlaylistCreateRequest>> violations = validator.validate(request);

    // then
    assertThat(violations).hasSize(1);
    assertThat(violations.iterator().next().getPropertyPath().toString())
        .isEqualTo("description");
  }


}