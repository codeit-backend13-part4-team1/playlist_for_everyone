package com.codeit.sb13.playlist_for_everyone.domain.auth.repository.impl;

import com.codeit.sb13.playlist_for_everyone.domain.auth.repository.SessionStartResult;
import com.codeit.sb13.playlist_for_everyone.domain.auth.repository.UserSessionRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Testcontainers
@DisplayName("Redis 사용자 로그인 세션 저장소")
class RedisUserSessionRepositoryTest {



    @Container
    static final GenericContainer<?> redis = new GenericContainer<>("redis:7.4-alpine")
            .withExposedPorts(6379);

    private static final Duration TTL = Duration.ofMinutes(10);

    private LettuceConnectionFactory connectionFactory;
    private StringRedisTemplate redisTemplate;

    // 인터페이스를 통해 저장소 기능을 사용한다.
    private UserSessionRepository repository;

    private UUID userId;

    @BeforeEach
    void setUp() {
        connectionFactory = new LettuceConnectionFactory(redis.getHost(), redis.getMappedPort(6379));
        connectionFactory.afterPropertiesSet();

        redisTemplate = new StringRedisTemplate(connectionFactory);
        repository = new RedisUserSessionRepository(redisTemplate);

        userId = UUID.randomUUID();
    }

    @AfterEach
    void tearDown() {
        if (connectionFactory != null) {
            connectionFactory.destroy();
        }
    }

    @Test
    @DisplayName("첫 로그인은 새 세션을 시작하고 교체된 세션이 없다.")
    void 첫_로그인은_새_세션을_시작한다() {
        SessionStartResult result = repository.start(userId, TTL);

        assertThat(repository.isCurrent(userId, result.sessionId())).isTrue();
        assertThat(result.replaced()).isEmpty();
    }

    @Test
    @DisplayName("세션 키에 TTL이 설정된다.")
    void 세션에_TTL이_설정된다 () {
        repository.start(userId, TTL);

        Long ttlSeconds = redisTemplate.getExpire("auth:session:" + userId);
        assertThat(ttlSeconds).isPositive().isLessThanOrEqualTo(TTL.toSeconds());
    }

    @Test
    @DisplayName("다시 로그인하면 이전 세션이 무효화되고 교체된 세션 ID를 반환한다")
    void 다시_로그인하면_이전_세션은_무효가_된다() {
        SessionStartResult first = repository.start(userId, TTL);
        SessionStartResult second = repository.start(userId, TTL);

        assertThat(repository.isCurrent(userId, first.sessionId())).isFalse();
        assertThat(repository.isCurrent(userId, second.sessionId())).isTrue();
        assertThat(second.replaced()).contains(first.sessionId());
    }

    @Test
    @DisplayName("다른 사용자의 세션에는 영향을 주지 않는다")
    void 다른_사용자_세션에는_영향이_없다() {
        UUID otherUserId = UUID.randomUUID();
        SessionStartResult other = repository.start(otherUserId, TTL);

        repository.start(userId, TTL);

        assertThat(repository.isCurrent(otherUserId, other.sessionId())).isTrue();
    }

    @Test
    @DisplayName("세션이 없는 사용자는 현재 세션이 아니다")
    void 세션이_없으면_현재_세션이_아니다() {
        assertThat(repository.isCurrent(userId, UUID.randomUUID())).isFalse();
    }

    @Test
    @DisplayName("현재 세션이면 유효기간을 연장한다")
    void 현재_세션이면_연장한다() {
        SessionStartResult result = repository.start(userId, Duration.ofSeconds(30));

        boolean extended = repository.extendIfCurrent(userId, result.sessionId(), TTL);

        assertThat(extended).isTrue();
        assertThat(redisTemplate.getExpire("auth:session:" + userId)).isGreaterThan(30L);
    }

    @Test
    @DisplayName("이전 세션으로는 새 세션의 유효기간을 바꾸지 않는다")
    void 이전_세션으로는_연장하지_않는다() {
        SessionStartResult first = repository.start(userId, TTL);
        repository.start(userId, Duration.ofSeconds(30));

        boolean extended = repository.extendIfCurrent(userId, first.sessionId(), Duration.ofHours(1));

        assertThat(extended).isFalse();
        assertThat(redisTemplate.getExpire("auth:session:" + userId)).isLessThanOrEqualTo(30L);
    }

    @Test
    @DisplayName("현재 세션이면 로그아웃 시 세션을 종료한다")
    void 현재_세션이면_종료한다() {
        SessionStartResult result = repository.start(userId, TTL);

        repository.endIfCurrent(userId, result.sessionId());

        assertThat(repository.isCurrent(userId, result.sessionId())).isFalse();
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
    @DisplayName("강제 종료하면 종료한 세션 ID를 반환하고 세션이 사라진다")
    void 강제_종료하면_세션이_사라진다() {
        SessionStartResult result = repository.start(userId, TTL);

        Optional<UUID> revoked = repository.revoke(userId);

        assertThat(revoked).contains(result.sessionId());
        assertThat(repository.isCurrent(userId, result.sessionId())).isFalse();
    }

    @Test
    @DisplayName("세션이 없는 사용자를 강제 종료하면 빈 값을 반환한다")
    void 세션이_없으면_강제_종료_결과가_비어_있다() {
        assertThat(repository.revoke(userId)).isEmpty();
    }

    @ParameterizedTest(name = "TTL {0}초")
    @ValueSource(longs = {0, -1})
    @DisplayName("유효기간이 0 이하면 예외가 발생한다")
    void 유효기간이_0_이하면_예외가_발생한다(long seconds) {
        assertThatThrownBy(() -> repository.start(userId, Duration.ofSeconds(seconds)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("유효기간이 1밀리초 미만이면 예외가 발생한다")
    void 유효기간이_1밀리초_미만이면_예외가_발생한다() {
        assertThatThrownBy(() -> repository.start(userId, Duration.ofNanos(1)))
                .isInstanceOf(IllegalArgumentException.class);
    }

}