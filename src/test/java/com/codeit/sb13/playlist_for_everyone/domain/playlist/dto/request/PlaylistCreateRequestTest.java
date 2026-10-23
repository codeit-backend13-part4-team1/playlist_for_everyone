package com.codeit.sb13.playlist_for_everyone.domain.playlist.dto.request;


import static org.assertj.core.api.Assertions.assertThat;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import java.util.Set;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
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
}