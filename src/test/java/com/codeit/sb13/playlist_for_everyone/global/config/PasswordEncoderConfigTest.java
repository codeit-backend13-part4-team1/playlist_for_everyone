package com.codeit.sb13.playlist_for_everyone.global.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.security.crypto.password.PasswordEncoder;

@DisplayName("비밀번호 인코더 설정")
class PasswordEncoderConfigTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(PasswordEncoderConfig.class);

    @Test
    @DisplayName("같은 비밀번호도 서로 다른 해시로 인코딩하며 올바른 비밀번호만 일치한다")
    void encodesPasswordsAndRejectsIncorrectPasswords() {
        contextRunner.run(context -> {
            assertThat(context).hasSingleBean(PasswordEncoder.class);
            PasswordEncoder encoder = context.getBean(PasswordEncoder.class);
            String password = "test-password-123!";

            String encoded = encoder.encode(password);
            String secondEncoded = encoder.encode(password);

            assertThat(encoded).isNotEqualTo(password).isNotEqualTo(secondEncoded);
            assertThat(encoder.matches(password, encoded)).isTrue();
            assertThat(encoder.matches(password, secondEncoded)).isTrue();
            assertThat(encoder.matches("incorrect-password", encoded)).isFalse();
        });
    }
}
