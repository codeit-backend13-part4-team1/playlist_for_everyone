package com.codeit.sb13.playlist_for_everyone.global.sse.config;

import com.codeit.sb13.playlist_for_everyone.global.config.SseConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("SSE 설정값 검증")
class SsePropertiesTest {

    private final ApplicationContextRunner contextRunner =
            new ApplicationContextRunner()
                    .withUserConfiguration(SseConfig.class);

    @Test
    @DisplayName("하트비트 간격이 0이면 설정 로딩에 실패한다.")
    void failToStartWhenHeartbeatIntervalIsZero(){
        contextRunner.withPropertyValues("everyone.sse.heartbeat-interval=0s",
                "everyone.sse.connection-timeout=30m")
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    @DisplayName("연결 제한 시간이 1밀리초 미만이면 설정 로딩에 실패한다.")
    void failsToStartWhenConnectionTimeoutIsLessThanOneMillisecond(){
        contextRunner.withPropertyValues("everyone.sse.heartbeat-interval=15s",
                        "everyone.sse.connection-timeout=500us")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure())
                            .hasRootCauseInstanceOf(IllegalArgumentException.class);
                });
    }

    @Test
    @DisplayName("연결 제한 시간이 정확히 1밀리초이면 설정 로딩에 성공한다.")
    void startSuccessfullyWhenConnectionTimeoutIsOneMillisecond(){
        contextRunner.withPropertyValues("everyone.sse.heartbeat-interval=15s",
                        "everyone.sse.connection-timeout=1ms")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    SseProperties properties = context.getBean(SseProperties.class);
                    assertThat(properties.connectionTimeout()).isEqualTo(Duration.ofMillis(1));
                });
    }

    @Test
    @DisplayName("하트비트 간격 설정이 누락되면 설정로딩에 실패한다.")
    void failsToStartWhenHeartbeatIntervalIsMissing(){
        contextRunner.withPropertyValues("everyone.sse.connection-timeout=30m")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure())
                            .hasRootCauseInstanceOf(IllegalArgumentException.class);
                });
    }

    @Test
    @DisplayName("하트비트 간격이 음수이면 설정 로딩에 실패한다.")
    void failsToStartWhenHeartbeatIntervalIsNegative(){
        contextRunner.withPropertyValues(
                "everyone.sse.heartbeat-interval=-1s",
                "everyone.sse.connection-timeout=30m"
        ).run(context ->{
            assertThat(context).hasFailed();
            assertThat(context.getStartupFailure())
                    .hasRootCauseInstanceOf(IllegalArgumentException.class);
        });
    }

    @Test
    @DisplayName("연결 제한 시간 설정이 누락되면 설정로딩에 실패한다.")
    void failsToStartWhenConnectionTimeoutIsMissing(){
        contextRunner.withPropertyValues("everyone.sse.heartbeat-interval=15s")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure())
                            .hasRootCauseInstanceOf(IllegalArgumentException.class);
                });
    }

    @Test
    @DisplayName("연결 제한 시간이 0이면 설정 로딩에 실패한다.")
    void failsToStartWhenConnectionTimeoutIsZero(){
        contextRunner.withPropertyValues(
                "everyone.sse.heartbeat-interval=15s",
                "everyone.sse.connection-timeout=0ms"
        ).run(context ->{
            assertThat(context).hasFailed();
            assertThat(context.getStartupFailure())
                    .hasRootCauseInstanceOf(IllegalArgumentException.class);
        });
    }

    @Test
    @DisplayName("연결 제한 시간이 음수이면 설정 로딩에 실패한다.")
    void failsToStartWhenConnectionTimeoutIsNegative(){
        contextRunner.withPropertyValues(
                "everyone.sse.heartbeat-interval=15s",
                "everyone.sse.connection-timeout=-1ms"
        ).run(context ->{
            assertThat(context).hasFailed();
            assertThat(context.getStartupFailure())
                    .hasRootCauseInstanceOf(IllegalArgumentException.class);
        });
    }

    @Test
    @DisplayName("정상 SSE 설정값이면 설정 로딩에 성공하고 값을 바인딩한다.")
    void bindValidSseProperties(){
        contextRunner.withPropertyValues("everyone.sse.heartbeat-interval=15s",
                        "everyone.sse.connection-timeout=30m")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    SseProperties properties = context.getBean(SseProperties.class);
                    assertThat(properties.connectionTimeout()).isEqualTo(Duration.ofMinutes(30));
                    assertThat(properties.heartbeatInterval()).isEqualTo(Duration.ofSeconds(15));
                });
    }
}