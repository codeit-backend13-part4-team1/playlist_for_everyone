package com.codeit.sb13.playlist_for_everyone.domain.auth.service;


import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("임시 비밀번호 생성")
class TemporaryPasswordGeneratorTest {

    private final TemporaryPasswordGenerator generator = new TemporaryPasswordGenerator();

    @RepeatedTest(50)
    @DisplayName("12자이고 대문자·소문자·숫자·특수문자를 모두 포함한다.")
    void 규칙에_맞는_임시_비밀번호를_만든다() {
        String password = generator.generate();

        assertThat(password).hasSize(TemporaryPasswordGenerator.LENGTH);
        assertThat(password).containsAnyOf(chars(TemporaryPasswordGenerator.UPPER));
        assertThat(password).containsAnyOf(chars(TemporaryPasswordGenerator.LOWER));
        assertThat(password).containsAnyOf(chars(TemporaryPasswordGenerator.NUMBERS));
        assertThat(password).containsAnyOf(chars(TemporaryPasswordGenerator.SPECIALS));
    }

    @RepeatedTest(50)
    @DisplayName("헷갈리기 쉬운 문자는 사용하지 않는다.")
    void 헷갈리기_쉬운_문자는_사용하지_않느다() {
        assertThat(generator.generate()).doesNotContain("0", "O", "1", "l", "I");
    }

    @Test
    @DisplayName("생성할 때마다 다른 값이 나온다.")
    void 생성할_때마다_다른_값이_나온다() {
        Set<String> passwords = new HashSet<>();
        for (int i = 0; i < 100; i++) {
            passwords.add(generator.generate());
        }

        assertThat(passwords).hasSize(100);
    }

    private static String[] chars(String source) {
        return source.chars().mapToObj(c -> String.valueOf((char) c)).toArray(String[]::new);
    }

}
