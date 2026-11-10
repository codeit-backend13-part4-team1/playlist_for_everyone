package com.codeit.sb13.playlist_for_everyone.global.sse.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("SSE 이력 설정 검증")
class SseHistoryPropertiesTest {

    public static final String SSE_HISTORY_KEY_PREFIX = "test:sse:history";
    public static final int SSE_MAX_EVENTS_PER_USER = 1000;
    public static final Duration SSE_HISTORY_RETENTION_DURATION = Duration.ofMinutes(10);

    @Test
    @DisplayName("보관 기간이 0이면 설정 생성을 거부한다.")
    void rejectsZeroRetention(){
        assertThatThrownBy(() -> new SseHistoryProperties(
                Duration.ZERO,
                SSE_MAX_EVENTS_PER_USER,
                SSE_HISTORY_KEY_PREFIX
        )).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("보관 기간이 null이면 설정 생성을 거부한다.")
    void rejectsNullRetention(){
        assertThatThrownBy(() -> new SseHistoryProperties(
                null,
                SSE_MAX_EVENTS_PER_USER,
                SSE_HISTORY_KEY_PREFIX
        )).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("보관 기간이 음수이면 설정 생성을 거부한다.")
    void rejectsNegativeRetention(){
        assertThatThrownBy(() -> new SseHistoryProperties(
                Duration.ofMillis(-1),
                SSE_MAX_EVENTS_PER_USER,
                SSE_HISTORY_KEY_PREFIX
        )).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("보관 기간이 양수여도 1밀리초 미만이면 설정 생성을 거부한다.")
    void rejectsRetentionLessThanOneMillisecond(){
        assertThatThrownBy(() -> new SseHistoryProperties(
                Duration.ofNanos(500_000),
                SSE_MAX_EVENTS_PER_USER,
                SSE_HISTORY_KEY_PREFIX
        )).isInstanceOf(IllegalArgumentException.class);
    }

    @ParameterizedTest(name = "maxEventsPerUser={0}")
    @ValueSource(ints = {0, -1})
    @DisplayName("사용자별 최대 이벤트 건수가 0 이하이면 설정 생성을 거부한다.")
    void rejectsNonPositiveMaxEventsPerUser(int maxEventsPerUser){
        assertThatThrownBy(() -> new SseHistoryProperties(
                SSE_HISTORY_RETENTION_DURATION,
                maxEventsPerUser,
                SSE_HISTORY_KEY_PREFIX
        )).isInstanceOf(IllegalArgumentException.class);
    }

    @ParameterizedTest(name = "[{index}] keyPrefix=[{0}]")
    @NullAndEmptySource
    @ValueSource(strings = {"    "})
    @DisplayName("키 접두사가 null이거나 빈 문자열 또는 공백이면 설정 생성을 거부한다.")
    void rejectsMissingOrBlankKeyPrefix(String keyPrefix){
        assertThatThrownBy(()-> new SseHistoryProperties(
                SSE_HISTORY_RETENTION_DURATION,
                SSE_MAX_EVENTS_PER_USER,
                keyPrefix
        )).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("보관 기간이 1밀리초이고 최대 이벤트건수가 1이면 설정 생성에 성공한다.")
    void acceptsMinimumAllowedValues(){
        assertThatCode(() -> new SseHistoryProperties(
                Duration.ofMillis(1),
                1,
                SSE_HISTORY_KEY_PREFIX
        )).doesNotThrowAnyException();
    }



}