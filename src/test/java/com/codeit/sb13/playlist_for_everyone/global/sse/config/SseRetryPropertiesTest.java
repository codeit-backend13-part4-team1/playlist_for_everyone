package com.codeit.sb13.playlist_for_everyone.global.sse.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("SSE 재시도 설정 검증")
class SseRetryPropertiesTest {

    @Test
    @DisplayName("초기 재시도 간격이 null이면 설정 생성을 거부한다.")
    void rejectsNullInitialDelay(){
        assertThatThrownBy(() -> new SseRetryProperties(null, Duration.ofSeconds(30), 3)
        ).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("최대 재시도 간격이 null이면 설정 생성을 거부한다.")
    void rejectsNullMaxDelay(){
        assertThatThrownBy(() -> new SseRetryProperties( Duration.ofSeconds(1), null, 3)
        ).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("초기 재시도 간격이 0이면 설정 생성을 거부한다.")
    void rejectsZeroInitialDelay(){
        assertThatThrownBy(() -> new SseRetryProperties( Duration.ZERO, Duration.ofSeconds(30), 3)
        ).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("최대 재시도 간격이 0이면 설정 생성을 거부한다.")
    void rejectsZeroMaxDelay(){
        assertThatThrownBy(() -> new SseRetryProperties( Duration.ofSeconds(1), Duration.ZERO, 3)
        ).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("초기 재시도 간격이 음수이면 설정 생성을 거부한다.")
    void rejectsNegativeInitialDelay(){
        assertThatThrownBy(() -> new SseRetryProperties( Duration.ofMillis(-1), Duration.ofSeconds(30), 3)
        ).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("최대 재시도 간격이 음수이면 설정 생성을 거부한다.")
    void rejectsNegativeMaxDelay(){
        assertThatThrownBy(() -> new SseRetryProperties( Duration.ofMillis(1), Duration.ofSeconds(-1), 3)
        ).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("초기 재시도 간격이 양수여도 1밀리초 미만이면 설정 생성을 거부한다.")
    void rejectsInitialDelayLessThanOneMillisecond(){
        assertThatThrownBy(() -> new SseRetryProperties( Duration.ofNanos(500_000), Duration.ofSeconds(30), 3)
        ).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("최대 재시도 간격이 양수여도 1밀리초미만이면 설정 생성을 거부한다.")
    void rejectsMaxDelayLessThanOneMillisecond(){
        assertThatThrownBy(() -> new SseRetryProperties( Duration.ofSeconds(1), Duration.ofNanos(500_000), 3)
        ).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("최대 재시도 간격이 초기 간격보다 작으면 설정 생성을 거부한다.")
    void rejectsMaxDelayLessThanInitialDelay(){
        assertThatThrownBy(() -> new SseRetryProperties(Duration.ofMillis(1500), Duration.ofMillis(1000), 3)
        ).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("초기 간격과 최대 간격이 모두 1밀리초이면 설정 생성에 성공한다.")
    void acceptsMinimumAllowedDelays(){
        assertThatCode(() -> new SseRetryProperties( Duration.ofMillis(1), Duration.ofMillis(1), 3)
        ).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("초기 간격이 1초이고 최대 간격이 30초이면 설정 생성에 성공한다.")
    void acceptsMaxDelayGreaterThanInitialDelay(){
        assertThatCode(() -> new SseRetryProperties( Duration.ofSeconds(1), Duration.ofSeconds(30), 3)
        ).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("최대 재시도 횟수가 0이면 설정 생성을 거부한다.")
    void rejectsZeroMaxRetries() {
        assertThatThrownBy(() -> new SseRetryProperties(
                Duration.ofSeconds(1), Duration.ofSeconds(30), 0
        )).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("최대 재시도 횟수가 음수이면 설정 생성을 거부한다.")
    void rejectsNegativeMaxRetries() {
        assertThatThrownBy(() -> new SseRetryProperties(
                Duration.ofSeconds(1), Duration.ofSeconds(30), -1
        )).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("최대 재시도 횟수가 최소값 1이면 설정 생성에 성공한다.")
    void acceptsMinimumMaxRetries() {
        SseRetryProperties properties = new SseRetryProperties(
                Duration.ofSeconds(1), Duration.ofSeconds(30), 1
        );

        assertThat(properties.maxRetries()).isEqualTo(1);
    }
}
