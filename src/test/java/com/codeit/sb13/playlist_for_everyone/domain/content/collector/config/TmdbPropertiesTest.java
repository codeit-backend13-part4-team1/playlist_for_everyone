package com.codeit.sb13.playlist_for_everyone.domain.content.collector.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.context.properties.bind.validation.BindValidationException;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.validation.autoconfigure.ValidationAutoConfiguration;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;

class TmdbPropertiesTest {

    private final ApplicationContextRunner contextRunner =
            new ApplicationContextRunner()
                    .withConfiguration(
                            AutoConfigurations.of(ValidationAutoConfiguration.class)
                    )
                    .withUserConfiguration(TestConfig.class);

    @ParameterizedTest
    @ValueSource(strings = {"", "   "})
    void 토큰이_비어있으면_설정_로딩에_실패한다(String token) {
        contextRunner
                .withPropertyValues("everyone.tmdb.access-token=" + token)
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure())
                            .hasRootCauseInstanceOf(BindValidationException.class)
                            .hasStackTraceContaining("TMDB 접근 토큰은 필수입니다.");
                });
    }

    @Test
    void 토큰이_설정되어있으면_설정_로딩에_성공한다() {
        contextRunner
                .withPropertyValues("everyone.tmdb.access-token=test-token")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context.getBean(TmdbProperties.class).getAccessToken())
                            .isEqualTo("test-token");
                });
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(TmdbProperties.class)
    static class TestConfig {
    }
}