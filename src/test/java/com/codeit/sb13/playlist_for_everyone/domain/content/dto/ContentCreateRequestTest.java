package com.codeit.sb13.playlist_for_everyone.domain.content.dto;

import com.codeit.sb13.playlist_for_everyone.domain.content.dto.request.ContentCreateRequest;
import com.codeit.sb13.playlist_for_everyone.domain.content.entity.ContentType;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;


@DisplayName("ContentCreateRequest 유효성 검사 테스트")
public class ContentCreateRequestTest {

    private Validator validator;

    @BeforeEach
    void setUp() {
        validator = Validation.buildDefaultValidatorFactory()
                .getValidator();
    }

    @Test
    @DisplayName("타입이 비어 있으면 유효성 검사에 실패한다.")
    void validate_nullType() {
        // given
        ContentCreateRequest request = new ContentCreateRequest(
                null,
                "너말고다른연애",
                "KBS 드라마입니다.",
                List.of("드라마", "KBS")
        );

        // when
        Set<ConstraintViolation<ContentCreateRequest>> violations =
                validator.validate(request);

        // then
        assertThat(violations)
                .extracting(v -> v.getPropertyPath().toString())
                .containsExactly("type");
    }

    @Test
    @DisplayName("제목이 비어 있으면 유효성 검사에 실패한다.")
    void validate_blankTitle() {
        // given
        ContentCreateRequest request = new ContentCreateRequest(
                ContentType.tvSeries,
                "",
                "KBS 드라마입니다.",
                List.of("드라마", "KBS")
        );

        // when
        Set<ConstraintViolation<ContentCreateRequest>> violations =
                validator.validate(request);

        // then
        assertThat(violations)
                .extracting(v -> v.getPropertyPath().toString())
                .contains("title");
    }

    @Test
    @DisplayName("설명이 공백이면 유효성 검사에 실패한다.")
    void validate_blankDescription() {
        // given
        ContentCreateRequest request = new ContentCreateRequest(
                ContentType.tvSeries,
                "너말고다른연애",
                "  ",
                List.of("드라마", "KBS")
        );

        // when
        Set<ConstraintViolation<ContentCreateRequest>> violations =
                validator.validate(request);

        // then
        assertThat(violations)
                .extracting(v -> v.getPropertyPath().toString())
                .contains("description");
    }

    @Test
    @DisplayName("태그가 없어도 유효성 검사를 통과한다.")
    void validate_withoutTags() {
        // given
        ContentCreateRequest request = new ContentCreateRequest(
                ContentType.tvSeries,
                "너말고다른연애",
                "KBS 드라마 입니다.",
                List.of()
        );

        // when
        Set<ConstraintViolation<ContentCreateRequest>> violations =
                validator.validate(request);

        // then
        assertThat(violations).isEmpty();
    }

}
