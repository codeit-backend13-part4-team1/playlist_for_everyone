package com.codeit.sb13.playlist_for_everyone.global.config;

import com.codeit.sb13.playlist_for_everyone.global.sse.config.SseHistoryProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("SSE 이력 설정 빈 등록 및 바인딩")
class SseHistoryConfigTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(SseHistoryConfig.class);

    @Test
    @DisplayName("정상 이력 설정이면 설정 빈이 등록되고 각 값이 바인딩된다.")
    void registerAndBindsValidHistoryProperties(){
        contextRunner.withPropertyValues(
                "everyone.sse.history.retention=2m",
                "everyone.sse.history.max-events-per-user=50",
                "everyone.sse.history.key-prefix=test:sse:history"
        ).run(context ->{
            assertThat(context).hasNotFailed();
            assertThat(context).hasSingleBean(SseHistoryProperties.class);

            SseHistoryProperties properties = context.getBean(SseHistoryProperties.class);
            assertThat(properties.retention()).isEqualTo(Duration.ofMinutes(2));
            assertThat(properties.maxEventsPerUser()).isEqualTo(50);
            assertThat(properties.keyPrefix()).isEqualTo("test:sse:history");
        });
    }

    @Test
    @DisplayName("보관 기간이 0이면 이력 설정 검증으로 컨텍스트 시작에 실패한다.")
    void failsToStartWhenRetentionIsZero(){
        contextRunner.withPropertyValues(
                "everyone.sse.history.retention=0s",
                "everyone.sse.history.max-events-per-user=50",
                "everyone.sse.history.key-prefix=test:sse:history"
        ).run(context ->{
            assertThat(context).hasFailed();
            assertThat(context.getStartupFailure())
                    .hasRootCauseInstanceOf(IllegalArgumentException.class);
        });
    }

}