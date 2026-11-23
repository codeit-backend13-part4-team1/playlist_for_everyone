package com.codeit.sb13.playlist_for_everyone.domain.user.dto.request;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("프로필 변경 요청 검증")
class UserUpdateRequestTest {

    private static ValidatorFactory factory;
    private static Validator validator;

    @BeforeAll
    static void setUp() {
        factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @AfterAll
    static void tearDown() {
        factory.close();
    }

    @Test
    @DisplayName("이름이 있으면 검증을 통과한다.")
    void 이름이_있으면_검증을_통과한다() {
        UserUpdateRequest request = new UserUpdateRequest("새 이름");

        assertThat(validator.validate(request)).isEmpty();
    }

    @ParameterizedTest(name = "이름=[{0}]")
    @NullAndEmptySource
    @ValueSource(strings = {" ", "\t"})
    @DisplayName("이름이 없거나 공백이면 검증에 실패한다")
    void 이름이_없거나_공백이면_검증에_실패한다(String name) {
        UserUpdateRequest request = new UserUpdateRequest(name);

        assertThat(validator.validate(request))
                .extracting(violation -> violation.getPropertyPath().toString())
                .containsExactly("name");
    }

    @Test
    @DisplayName("이름은 100자까지 허용한다")
    void 이름은_100자까지_허용한다() {
        UserUpdateRequest request = new UserUpdateRequest("가".repeat(100));

        assertThat(validator.validate(request)).isEmpty();
    }

    @Test
    @DisplayName("이름이 100자를 넘으면 검증에 실패한다")
    void 이름이_100자를_넘으면_검증에_실패한다() {
        UserUpdateRequest request = new UserUpdateRequest("가".repeat(101));

        assertThat(validator.validate(request))
                .extracting(violation -> violation.getPropertyPath().toString())
                .containsExactly("name");
    }
}