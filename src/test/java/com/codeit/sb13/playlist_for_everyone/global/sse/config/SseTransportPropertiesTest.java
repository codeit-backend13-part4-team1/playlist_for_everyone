package com.codeit.sb13.playlist_for_everyone.global.sse.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("SSE 전송 설정 검증")
class SseTransportPropertiesTest {

    private final ApplicationContextRunner contextRunner =
            new ApplicationContextRunner()
                    .withUserConfiguration(TestConfig.class);

    @TestConfiguration(proxyBeanMethods = false)
    @EnableConfigurationProperties(SseTransportProperties.class)
    static class TestConfig{

    }

    @Test
    @DisplayName("지원하지 않는 전송 방식이면 설정 로딩에 실패한다.")
    void failsToStartWhenTransportIsUnsupported(){
        contextRunner.withPropertyValues(
                "everyone.sse.transport=unsupported",
                "everyone.sse.redis.channel=everyone:sse:events"
        ).run(context -> {
            assertThat(context).hasFailed();
            assertThat(context.getStartupFailure())
                    .hasRootCauseInstanceOf(IllegalArgumentException.class);
        });
    }
}