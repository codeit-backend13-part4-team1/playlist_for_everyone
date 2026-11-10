package com.codeit.sb13.playlist_for_everyone.domain.user.dto.request;

import com.codeit.sb13.playlist_for_everyone.domain.user.entity.UserSortBy;
import com.codeit.sb13.playlist_for_everyone.global.domain.SortDirection;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("사용자 목록 조회 요청 검증")
class UserSearchRequestTest {

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

    // 검증에 필요한 값만 받고, 필터는 비워 둔 요청을 만든다.
    private static UserSearchRequest request(UserSortBy sortBy, String cursor, UUID idAfter, Integer limit) {
        return new UserSearchRequest(null, null, null, cursor, idAfter, limit,
                SortDirection.ASCENDING, sortBy);
    }

    @Test
    @DisplayName("프론트 기본 요청(limit 20, 이름 오름차순)은 통과한다.")
    void 프론트_기본_요청은_통과한다() {
        assertThat(validator.validate(request(UserSortBy.NAME, null, null, 20))).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -1, 101})
    @DisplayName("limit이 1~100 범위를 벗어나면 거부한다.")
    void limit이_범위를_벗어나면_거부한다(int limit) {
        assertThat(validator.validate(request(UserSortBy.NAME, null, null, limit))).isNotEmpty();
    }

    @Test
    @DisplayName("cursor만 있고 idAfter가 없으면 거부한다.")
    void cursor만_있으면_거부한다() {
        assertThat(validator.validate(request(UserSortBy.NAME, "kim", null, 20))).isNotEmpty();
    }

    @Test
    @DisplayName("idAfter만 있고 cursor가 없으면 거부한다.")
    void idAfter만_있으면_거부한다() {
        assertThat(validator.validate(request(UserSortBy.NAME, null, UUID.randomUUID(), 20))).isNotEmpty();
    }

    @ParameterizedTest
    @CsvSource({
            "NAME, 아무 이름",
            "EMAIL, a@b.com",
            "CREATED_AT, 2026-11-10T15:30:00",
            "IS_LOCKED, true",
            "IS_LOCKED, false",
            "ROLE, ADMIN"
    })
    @DisplayName("정렬 기준에 맞는 cursor는 통과한다.")
    void 정렬_기준에_맞는_cursor는_통과한다(UserSortBy sortBy, String cursor) {
        assertThat(validator.validate(request(sortBy, cursor, UUID.randomUUID(), 20))).isEmpty();
    }

    @ParameterizedTest
    @CsvSource({
            "CREATED_AT, 어제",
            "IS_LOCKED, yes",
            "ROLE, MANAGER"
    })
    @DisplayName("정렬 기준과 맞지 않는 cursor는 거부한다.")
    void 정렬_기준과_맞지_않는_cursor는_거부한다(UserSortBy sortBy, String cursor) {
        assertThat(validator.validate(request(sortBy, cursor, UUID.randomUUID(), 20))).isNotEmpty();
    }

}