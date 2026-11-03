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

class SportsDbPropertiesTest {

    private final ApplicationContextRunner contextRunner =
            new ApplicationContextRunner()
                    .withConfiguration(
                            AutoConfigurations.of(ValidationAutoConfiguration.class)
                    )
                    .withUserConfiguration(TestConfig.class);

    @ParameterizedTest
    @ValueSource(strings = {"", "   "})
    void API_키가_비어있으면_설정_로딩에_실패한다(String apiKey) {
        contextRunner
                .withPropertyValues(
                        "everyone.sportsdb.base-url=https://sportsdb.test",
                        "everyone.sportsdb.api-key=" + apiKey
                )
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure())
                            .hasRootCauseInstanceOf(BindValidationException.class)
                            .hasStackTraceContaining(
                                    "TheSportsDB API 키는 필수입니다."
                            );
                });
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   "})
    void 주소가_비어있으면_설정_로딩에_실패한다(String baseUrl) {
        contextRunner
                .withPropertyValues(
                        "everyone.sportsdb.base-url=" + baseUrl,
                        "everyone.sportsdb.api-key=test-key"
                )
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure())
                            .hasRootCauseInstanceOf(BindValidationException.class)
                            .hasStackTraceContaining("baseUrl");
                });
    }

    @Test
    void 주소와_API_키가_있으면_설정_로딩에_성공한다() {
        contextRunner
                .withPropertyValues(
                        "everyone.sportsdb.base-url=https://sportsdb.test",
                        "everyone.sportsdb.api-key=test-key"
                )
                .run(context -> {
                    assertThat(context).hasNotFailed();

                    var properties = context.getBean(SportsDbProperties.class);
                    assertThat(properties.getBaseUrl())
                            .isEqualTo("https://sportsdb.test");
                    assertThat(properties.getApiKey()).isEqualTo("test-key");
                });
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(SportsDbProperties.class)
    static class TestConfig {
    }
}