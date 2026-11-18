package com.codeit.sb13.playlist_for_everyone.domain.conversation.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.test.util.TestPropertyValues;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("DM 전송 설정 검증")
class DirectMessageTransportPropertiesTest {
    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(DirectMessageTransportConfig.class);

    @ParameterizedTest(name = "transport={0}")
    @ValueSource(strings = {"kafka", "unsupported"})
    @DisplayName("지원하지 않는 DM 전송 방식이면 설정 로딩에 실패한다.")
    void failsToStartWhenTransportIsUnsupported(String transport){
        contextRunner.withPropertyValues(
                "everyone.dm.websocket.transport=" + transport,
                "everyone.dm.websocket.redis.channel=test:dm:events"
        ).run(context -> {
            assertThat(context).hasFailed();
            assertThat(context.getStartupFailure())
                    .hasRootCauseInstanceOf(IllegalArgumentException.class);
        });
    }

    @Test
    @DisplayName("DM 전송 방식 설정이 누락되면 설정로딩에 실패한다.")
    void failsToStartWhenTransportIsMissing(){
        contextRunner.withPropertyValues(
                "everyone.dm.websocket.redis.channel=test:dm:events"
        ).run(context ->{
            assertThat(context).hasFailed();
            assertThat(context.getStartupFailure())
                    .hasRootCauseInstanceOf(IllegalArgumentException.class);
        });
    }

    @ParameterizedTest(name = "transport=[{0}]")
    @ValueSource(strings = {"", "  "})
    @DisplayName("DM 전송 방식이 빈 문자열이거나 공백이면 설정 로딩에 실패한다.")
    void failsToStartWhenTransportIsBlank(String transport){
        contextRunner.withInitializer(context ->{
            TestPropertyValues.of(Map.of(
                    "everyone.dm.websocket.transport", transport,
                    "everyone.dm.websocket.redis.channel", "test:dm:events"
            )).applyTo(context);
        }).run(context ->{
            assertThat(context).hasFailed();
            assertThat(context.getStartupFailure())
                    .hasRootCauseInstanceOf(IllegalArgumentException.class);
        });
    }

    @Test
    @DisplayName("Redis 전송 방식에서 Redis 설정이 누락되면 설정 로딩에 실패한다.")
    void failsToStartWhenRedisSettingsAreMissing(){
        contextRunner.withPropertyValues(
                "everyone.dm.websocket.transport=redis"
        ).run(context -> {
            assertThat(context).hasFailed();
            assertThat(context.getStartupFailure())
                    .hasRootCauseInstanceOf(IllegalArgumentException.class);
        });
    }

    @ParameterizedTest(name = "channel=[{0}]")
    @ValueSource(strings = {"", "  "})
    @DisplayName("DM Redis 채널이 빈 문자열이거나 공백이면 설정 로딩에 실패한다.")
    void failsToStartWhenRedisChannelIsBlank(String channel){
        contextRunner.withInitializer(context ->{
            TestPropertyValues.of(Map.of(
                    "everyone.dm.websocket.transport", "redis",
                    "everyone.dm.websocket.redis.channel", channel
            )).applyTo(context);
        }).run(context -> {
            assertThat(context).hasFailed();
            assertThat(context.getStartupFailure())
                    .hasRootCauseInstanceOf(IllegalArgumentException.class);
        });
    }
}