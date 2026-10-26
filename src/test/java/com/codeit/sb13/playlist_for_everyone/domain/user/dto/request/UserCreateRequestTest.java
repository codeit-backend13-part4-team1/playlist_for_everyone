package com.codeit.sb13.playlist_for_everyone.domain.user.dto.request;

import jakarta.validation.Validation;
import jakarta.validation.ValidatorFactory;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.security.core.parameters.P;

import static org.assertj.core.api.Assertions.assertThat;


@DisplayName("회원가입 요청 검증")
class UserCreateRequestTest {

    @ParameterizedTest(name = "{0} x {1}: 허용 여부={2}")
    @CsvSource({
            "a, 72, true",
            "a, 73, false",
            "가, 24, true",
            "가, 25, false",
            "😀, 18, true",
            "😀, 19, false"
    })
    @DisplayName("비밀번호의 UTF-8 바이트 길이를 검증한다")
    void 비밀번호_UTF8_바이트_길이를_검증한다(String character, int repeatCount, boolean valid) {

        UserCreateRequest request = new UserCreateRequest(
                "test@gmail.com",
                "테스트",
                character.repeat(repeatCount)
        );

        try(ValidatorFactory factory =
                    Validation.buildDefaultValidatorFactory()) {

            var violations = factory.getValidator().validate(request);

            if (valid) {
                assertThat(violations).isEmpty();
            } else {
                assertThat(violations)
                        .extracting(violation ->
                                violation.getPropertyPath().toString())
                        .containsExactly("password");
            }

        }
    }
}