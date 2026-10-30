package com.codeit.sb13.playlist_for_everyone.global.sse.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
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

    @Test
    @DisplayName("정상 Redis 전송 설정이면 설정 로딩에 성공하고 값을 바인딩한다.")
    void bindValidSseTransportProperties(){
        contextRunner.withPropertyValues("everyone.sse.transport=redis",
                "everyone.sse.redis.channel=test:sse:events")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    SseTransportProperties properties = context.getBean(SseTransportProperties.class);
                    assertThat(properties.transport()).isEqualTo("redis");
                    assertThat(properties.redis()).isNotNull();
                    assertThat(properties.redis().channel()).isEqualTo("test:sse:events");
                });
    }

    @Test
    @DisplayName("전송 방식 설정이 누락되면 설정 로딩에 실패한다.")
    void failsToStartWhenTransportIsMissing(){
        contextRunner.withPropertyValues(
                "everyone.sse.redis.channel=everyone:sse:events"
        ).run(context -> {
            assertThat(context).hasFailed();
            assertThat(context.getStartupFailure())
                    .hasRootCauseInstanceOf(IllegalArgumentException.class);
        });
    }

    @ParameterizedTest(name="transport={0}")
    @ValueSource(strings = {"REDIS", "ReDiS"})
    @DisplayName("전송 방식의 대소문자가 달라도 Redis 설정 로딩에 성공한다.")
    void startsSuccessfullyRegardlessOfTransportCase(String transport){
        contextRunner.withPropertyValues(
                "everyone.sse.transport=" + transport,
                "everyone.sse.redis.channel=everyone:sse:events"
        ).run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).hasSingleBean(SseTransportProperties.class);
        });
    }

    @Test
    @DisplayName("Redis 전송 방식에서 Redis 설정이 누락되면 설정 로딩에 실패한다.")
    void failsToStartWhenRedisSettingsAreMissing(){
        contextRunner.withPropertyValues(
                "everyone.sse.transport=redis"
        ).run(context -> {
            assertThat(context).hasFailed();
            assertThat(context.getStartupFailure())
                    .hasRootCauseInstanceOf(IllegalArgumentException.class);
        });
    }

}