package com.codeit.sb13.testsupport.auth;

import com.codeit.sb13.playlist_for_everyone.domain.auth.repository.SessionStartResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

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

}
