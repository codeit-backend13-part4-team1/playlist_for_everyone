package com.codeit.sb13.playlist_for_everyone.global.config;

import com.codeit.sb13.playlist_for_everyone.global.sse.config.SseRetryProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import java.time.Duration;

import static org.assertj.core.api.Assertions.*;

@DisplayName("SSE 재시도 설정 빈 등록 및 바인딩")
class SseRetryConfigTest {
    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(SseRetryConfig.class);

    @Test
    @DisplayName("정상 재시도 설정이면 설정 빈이 등록되고 간격과 최대 횟수가 바인딩된다.")
    void registerAndBindsValidRetryProperties(){
        contextRunner.withPropertyValues(
                "everyone.sse.retry.initial-delay=2s",
                "everyone.sse.retry.max-delay=10s",
                "everyone.sse.retry.max-retries=5"
        ).run(context ->{
            assertThat(context).hasNotFailed();
            assertThat(context).hasSingleBean(SseRetryProperties.class);

            SseRetryProperties properties = context.getBean(SseRetryProperties.class);
            assertThat(properties.initialDelay())
                    .isEqualTo(Duration.ofSeconds(2));
            assertThat(properties.maxDelay())
                    .isEqualTo(Duration.ofSeconds(10));
            assertThat(properties.maxRetries()).isEqualTo(5);
        });
    }

    @Test
    @DisplayName("최대 간격이 초기 간격보다 작으면 재시도 설정 검증으로 컨텍스트 시작에 실패한다.")
    void failsToStartWhenMaxDelayIsLessThanInitialDelay(){
        contextRunner.withPropertyValues(
                "everyone.sse.retry.initial-delay=2s",
                "everyone.sse.retry.max-delay=1s",
                "everyone.sse.retry.max-retries=3"
        ).run(context ->{
            assertThat(context).hasFailed();
            assertThat(context.getStartupFailure())
                    .hasRootCauseInstanceOf(IllegalArgumentException.class);
        });
    }

    @Test
    @DisplayName("최대 재시도 횟수가 0이면 설정 검증으로 컨텍스트 시작에 실패한다.")
    void failsToStartWhenMaxRetriesIsZero() {
        contextRunner.withPropertyValues(
                "everyone.sse.retry.initial-delay=1s",
                "everyone.sse.retry.max-delay=30s",
                "everyone.sse.retry.max-retries=0"
        ).run(context -> {
            assertThat(context).hasFailed();
            assertThat(context.getStartupFailure())
                    .hasRootCauseInstanceOf(IllegalArgumentException.class);
        });
    }
}
