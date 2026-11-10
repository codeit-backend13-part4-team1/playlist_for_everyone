package com.codeit.sb13.testsupport.auth;

import com.codeit.sb13.playlist_for_everyone.domain.auth.repository.SessionStartResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.time.Duration;
import java.util.UUID;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 테스트용 저장소가 Redis 저장소와 같은 규칙으로 동작하는지 확인한다.
 */
@DisplayName("테스트용 메모리 로그인 세션 저장소")
class InMemoryUserSessionRepositoryTest {

    private static final Duration TTL = Duration.ofMinutes(10);

    private final InMemoryUserSessionRepository repository = new InMemoryUserSessionRepository();
    private final UUID userId = UUID.randomUUID();

    @Test
    @DisplayName("다시 로그인하면 이전 세션은 무효가 되고 교체된 세션 ID를 반환한다")
    void 다시_로그인하면_이전_세션은_무효가_된다() {
        SessionStartResult first = repository.start(userId, TTL);
        SessionStartResult second = repository.start(userId, TTL);

        assertThat(repository.isCurrent(userId, first.sessionId())).isFalse();
        assertThat(repository.isCurrent(userId, second.sessionId())).isTrue();
        assertThat(second.replaced()).contains(first.sessionId());
    }

    @Test
    @DisplayName("이전 세션으로 로그아웃해도 새 세션은 유지된다")
    void 이전_세션으로_로그아웃해도_새_세션은_유지된다() {
        SessionStartResult first = repository.start(userId, TTL);
        SessionStartResult second = repository.start(userId, TTL);

        repository.endIfCurrent(userId, first.sessionId());

        assertThat(repository.isCurrent(userId, second.sessionId())).isTrue();
    }

    @Test
    @DisplayName("강제 종료하면 종료한 세션 ID를 반환한다")
    void 강제_종료하면_세션_ID를_반환한다() {
        SessionStartResult result = repository.start(userId, TTL);

        assertThat(repository.revoke(userId)).contains(result.sessionId());
        assertThat(repository.isCurrent(userId, result.sessionId())).isFalse();
    }
    @Test
    @DisplayName("현재 세션이면 연장하고, 이전 세션이면 연장하지 않는다")
    void 현재_세션일_때만_연장한다() {
        SessionStartResult first = repository.start(userId, TTL);
        SessionStartResult second = repository.start(userId, TTL);

        assertThat(repository.extendIfCurrent(userId, second.sessionId(), TTL)).isTrue();
        assertThat(repository.extendIfCurrent(userId, first.sessionId(), TTL)).isFalse();
    }

    @Test
    @DisplayName("사용자 ID나 세션 ID가 null이면 현재 세션이 아니다")
    void null이면_현재_세션이_아니다() {
        SessionStartResult result = repository.start(userId, TTL);

        assertThat(repository.isCurrent(null, result.sessionId())).isFalse();
        assertThat(repository.isCurrent(userId, null)).isFalse();
    }

    @ParameterizedTest(name = "TTL {0}")
    @MethodSource("invalidTtls")
    @DisplayName("Redis 저장소와 같이 1밀리초 미만의 유효기간을 거부한다")
    void 잘못된_유효기간을_거부한다(Duration ttl) {
        assertThatThrownBy(() -> repository.start(userId, ttl))
                .isInstanceOf(IllegalArgumentException.class);
    }

    static Stream<Duration> invalidTtls() {
        return Stream.of(Duration.ZERO, Duration.ofSeconds(-1), Duration.ofNanos(1));
    }

    @Test
    @DisplayName("유효기간이 null이면 예외가 발생한다")
    void 유효기간이_null이면_예외가_발생한다() {
        assertThatThrownBy(() -> repository.start(userId, null))
                .isInstanceOf(NullPointerException.class);
    }
}

