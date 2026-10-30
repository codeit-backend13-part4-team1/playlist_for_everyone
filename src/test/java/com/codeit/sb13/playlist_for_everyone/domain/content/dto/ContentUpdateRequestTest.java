package com.codeit.sb13.playlist_for_everyone.domain.content.dto;

import com.codeit.sb13.playlist_for_everyone.domain.content.dto.request.ContentUpdateRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("ContentUpdateRequest 유효성 검증 테스트")
public class ContentUpdateRequestTest {

    private final Validator validator = Validation
            .buildDefaultValidatorFactory()
            .getValidator();


    @Test
    @DisplayName("제목과 설명이 유효하면 검증을 통과한다.")
    void validRequest_success() {
        // given
        ContentUpdateRequest request = new ContentUpdateRequest(
                "너 말고 다른 연애",
                "서강준, 안은진 출연",
                List.of("드라마", "로맨스")
        );

        // when
        Set<ConstraintViolation<ContentUpdateRequest>> violations =
                validator.validate(request);

        // then
        assertThat(violations).isEmpty();
    }

    @Test
    @DisplayName("제목과 설명이 null이면 검증에 성공한다.")
    void validNullFields() {
        // given
        ContentUpdateRequest request = new ContentUpdateRequest(
                null,
                null,
                List.of("드라마", "로맨스")
        );

        // when
        Set<ConstraintViolation<ContentUpdateRequest>> violations =
                validator.validate(request);

        // then
        assertThat(violations).isEmpty();
    }

    @Test
    @DisplayName("제목이 비어 있으면 검증에 실패한다.")
    void invalidTitle() {
        // given
        ContentUpdateRequest request = new ContentUpdateRequest(
                "   ",
                "드라마 입니다.",
                List.of("드라마", "로맨스")
        );

        // when
        Set<ConstraintViolation<ContentUpdateRequest>> violations =
                validator.validate(request);

        // then
        assertThat(violations)
                .extracting(v -> v.getPropertyPath().toString())
                .containsExactly("title");
    }

    @Test
    @DisplayName("제목에 줄바꿈이 포함되면 검증에 실패한다.")
    void update_titleWithNewline_invalid() {
        // given
        ContentUpdateRequest request = new ContentUpdateRequest(
                "첫째 줄\n둘째 줄",
                null,
                null
        );

        // when
        Set<ConstraintViolation<ContentUpdateRequest>> violations =
                validator.validate(request);

        // then
        assertThat(violations)
                .extracting(v -> v.getPropertyPath().toString())
                .contains("title");
    }

    @Test
    @DisplayName("설명이 공백이면 검증에 실패한다.")
    void invalidDescription() {
        // given
        ContentUpdateRequest request = new ContentUpdateRequest(
                "너 말고 다른 연애",
                "",
                List.of("드라마", "로맨스")
        );

        // when
        Set<ConstraintViolation<ContentUpdateRequest>> violations =
                validator.validate(request);

        // then
        assertThat(violations)
                .extracting(v -> v.getPropertyPath().toString())
                .containsExactly("description");
    }

    @Test
    @DisplayName("태그가 null이어도 검증을 통과한다.")
    void nullTags_success() {
        // given
        ContentUpdateRequest request = new ContentUpdateRequest(
                "너 말고 다른 연애",
                "서강준, 안은진 주연",
                null
        );

        // when
        Set<ConstraintViolation<ContentUpdateRequest>> violations =
                validator.validate(request);

        // then
        assertThat(violations).isEmpty();
    }

    @Test
    @DisplayName("설명에 줄바꿈이 포함되어도 검증을 통과한다.")
    void descriptionWithNewline_success() {
        // given
        ContentUpdateRequest request = new ContentUpdateRequest(
                null,
                "첫째 줄\n둘째 줄",
                null
        );

        // when
        Set<ConstraintViolation<ContentUpdateRequest>> violations =
                validator.validate(request);

        // then
        assertThat(violations).isEmpty();
    }

    @Test
    @DisplayName("설명이 공백과 줄바꿈만 있으면 검증에 실패한다.")
    void descriptionOnlyWhitespace_invalid() {
        // given
        ContentUpdateRequest request = new ContentUpdateRequest(
                null,
                " \n \t ",
                null
        );

        // when
        Set<ConstraintViolation<ContentUpdateRequest>> violations =
                validator.validate(request);

        // then
        assertThat(violations)
                .extracting(v -> v.getPropertyPath().toString())
                .containsExactly("description");
    }

}
