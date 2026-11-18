package com.codeit.sb13.playlist_for_everyone.domain.user.entity;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class UserSortByTest {

    @ParameterizedTest
    @CsvSource({
            "name, NAME",
            "email, EMAIL",
            "createdAt, CREATED_AT",
            "isLocked, IS_LOCKED",
            "role, ROLE"
    })
    @DisplayName("API 정렬 값을 정렬 기준으로 변환한다.")
    void API_정렬_값을_정렬_기준으로_변환한다(String value, UserSortBy expected) {
        assertThat(UserSortBy.from(value)).isEqualTo(expected);
    }

    @Test
    @DisplayName("지원하지 않는 정렬 값이면 예외가 발생한다.")
    void 지원하지_않는_정렬_값이면_예외가_발생한다() {
        assertThatThrownBy(() -> UserSortBy.from("password"))
                .isInstanceOf(IllegalArgumentException.class);
    }

}