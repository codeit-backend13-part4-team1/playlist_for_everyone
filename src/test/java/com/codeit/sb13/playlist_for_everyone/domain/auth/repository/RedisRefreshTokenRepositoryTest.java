package com.codeit.sb13.playlist_for_everyone.domain.auth.repository;

import com.codeit.sb13.playlist_for_everyone.domain.auth.repository.impl.RedisRefreshTokenRepository;
import kotlin.DslMarker;
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

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Testcontainers
@DisplayName("Redis Refresh Token 저장소")
class RedisRefreshTokenRepositoryTest {

    @Container
    static final GenericContainer<?> redis = new GenericContainer<>("redis:7.4-alpine")
            .withExposedPorts(6379);

    private LettuceConnectionFactory connectionFactory;
    private StringRedisTemplate redisTemplate;

    // 인터페이스를 통헤 저장소 가능을 사용한다.
    private RefreshTokenRepository repository;


    private UUID userId;
    private String refreshToken;

    @BeforeEach
    void setUp() {
        // 개발용 Redis가 아닌 테스트 컨테이너에 연결한다.
        connectionFactory = new LettuceConnectionFactory(
                redis.getHost(),
                redis.getMappedPort(6379)
        );
        connectionFactory.afterPropertiesSet();

        redisTemplate = new StringRedisTemplate(connectionFactory);

        // 검증 대상은 실제 Redis 구현체다.
        repository = new RedisRefreshTokenRepository(redisTemplate);

        // 테스트마다 다른 키를 사용해 서로 영향을 주지 않도록 한다.
        userId = UUID.randomUUID();
        refreshToken = "test-refresh-token-" + UUID.randomUUID();
    }

    @AfterEach
    void tearDown() {
        // 각 테스트에서 생성한 연결 자원을 정리한다.
        // 데이터는 테스트 컨테이너 종료 시 함께 제거된다.
        if (connectionFactory != null) {
            connectionFactory.destroy();
        }
    }

    @Test
    @DisplayName("토큰을 저장하면 해당 사용자 ID를 조회할 수 있다.")
    void 저장한_토큰의_사용자_ID를_조회한다() {
        repository.save(userId, refreshToken, Duration.ofMinutes(10));

        assertThat(repository.findUserId(refreshToken))
                .contains(userId);
    }

    @Test
    @DisplayName("등록되지 않은 토큰은 빈 결과를 반환한다")
    void 등록되지_않은_토큰은_빈_결과를_반환한다() {
        assertThat(repository.findUserId(refreshToken)).isEmpty();
    }

    @Test
    @DisplayName("같은 사용자의 서로 다른 토큰을 각각 구분한다.")
    void 같은_사용자의_토큰을_각각_구분한다() {
        String anotherToken = "test-another-token-" + UUID.randomUUID();

        repository.save(userId, refreshToken, Duration.ofMinutes(10));
        repository.save(userId, anotherToken, Duration.ofMinutes(10));

        // 한 토큰의 삭제가 다른 토큰까지 삭제하면 안된다.
        repository.delete(refreshToken);

        assertThat(repository.findUserId(refreshToken)).isEmpty();
        assertThat(repository.findUserId(anotherToken)).contains(userId);
    }

    @Test
    @DisplayName("삭제한 토큰은 더 이상 조회되지 않는다.")
    void 삭제한_토큰은_조회되지_않는다() {
        repository.save(userId, refreshToken, Duration.ofMinutes(10));

        repository.delete(refreshToken);
        assertThat(repository.findUserId(refreshToken)).isEmpty();
    }

    @Test
    @DisplayName("없는 토큰을 반복 삭제해도 오류가 발생하지 않는다")
    void 없는_토큰을_반복_삭제할_수_없다() {
        // 예외가 발생하면 테스트가 실패한다.
        repository.delete(refreshToken);
        repository.delete(refreshToken);

        assertThat(repository.findUserId(refreshToken)).isEmpty();
    }

    @Test
    @DisplayName("토큰 해시를 키로 사용하고 사용자 ID와 TTL을 저장한다")
    void 토큰_해시와_사용자_ID와_TTL을_저장한다() {
        Duration ttl = Duration.ofMinutes(10);

        repository.save(userId, refreshToken, ttl);

        String key = redisKey(refreshToken);

        // 저장소의 조회 메서드와 독립적으로 실제 저장 형식을 확인한다.
        assertThat(redisTemplate.opsForValue().get(key))
                .isEqualTo(userId.toString());

        Long remainingMillis =
                redisTemplate.getExpire(key, TimeUnit.MILLISECONDS);
        // 만료 설정이 없다면 -1, 키가 없으면 -2가 반환된다.
        // 저장 후 시간이 흐르므로 최조 TTL과 정확히 같을 필요는 없다.
        assertThat(remainingMillis).isNotNull();
        assertThat(remainingMillis)
                .isPositive()
                .isLessThanOrEqualTo(ttl.toMillis());
    }

    @Test
    @DisplayName("TTL이 지나면 토큰을 조회할 수 없다")
    void TTL이_지나면_토큰이_만료된다() throws InterruptedException {

        repository.save(userId, refreshToken, Duration.ofSeconds(2));

        // 저장 자체가 누락된 구현이 통과하지 못하도록 먼저 확인한다.
        assertThat(repository.findUserId(refreshToken)).contains(userId);

        // Redis 서버의 실제 시간을 기준으로 만료를 확인하다.
        // 무한 대기하지 않도록 최대 대기시간을 둔다.
        long deadline = System.nanoTime() + Duration.ofSeconds(10).toNanos();

        while (System.nanoTime() < deadline && repository.findUserId(refreshToken).isPresent()) {
            Thread.sleep(100);
        }

        assertThat(repository.findUserId(refreshToken)).isEmpty();
    }

    @Test
    @DisplayName("토큰을 교체하면 기존 토큰을 폐기하고 새 토큰에 TTL이 적용된다.")
    void 토큰을_교체하면_기존_토큰의_재사용을_거절한다() {
        String newToken = "test-refresh-token-" + UUID.randomUUID();
        String retryToken = "test-retry-token-" + UUID.randomUUID();
        Duration newTtl = Duration.ofMinutes(5);

        repository.save(userId, refreshToken, Duration.ofMinutes(10));

        boolean rotated = repository.rotate(userId, refreshToken, newToken, newTtl);

        assertThat(rotated).isTrue();

        // 이전 토큰으로는 더 이상 재발급할 수 없어야 한다.
        assertThat(repository.findUserId(refreshToken)).isEmpty();

        // 새 토큰에는 동일한 사용자 ID가 연결되어야 한다.
        assertThat(repository.findUserId(newToken)).contains(userId);

        // 새 토큰에도 만료시간이 적용되어야 한다.
        Long remainingMillis = redisTemplate.getExpire(redisKey(newToken), TimeUnit.MILLISECONDS);

        assertThat(remainingMillis).isNotNull();
        assertThat(remainingMillis)
                .isPositive()
                .isLessThanOrEqualTo(newTtl.toMillis());

        // 이미 사용한 기존 토큰으로 다시 교체하려는 요청은 실패한다.
        boolean reused = repository.rotate(
                userId,
                refreshToken,
                retryToken,
                newTtl
        );

        assertThat(reused).isFalse();
        assertThat(repository.findUserId(retryToken)).isEmpty();

        // 실패한 재시도가 정상 발급된 토큰에 영향을 주면 안 된다.
        assertThat(repository.findUserId(newToken)).contains(userId);
    }

    @Test
    @DisplayName("기존 토큰이 등록되어 있지 않으면 새 토큰으로 저장하지 않는다.")
    void 미등록_토큰의_교체를_거절한다() {
        String newToken = "new-refresh-token-" + UUID.randomUUID();

        boolean rotated = repository.rotate(
                userId,
                refreshToken,
                newToken,
                Duration.ofMinutes(5)
        );
        assertThat(rotated).isFalse();
        assertThat(repository.findUserId(newToken)).isEmpty();
    }

    @Test
    @DisplayName("새 토큰이 이미 등록되어 있으면 두 토큰을 유지하고 교체를 거절한다.")
    void 새_토큰이_이미_등록되어_있으면_교체를_거절한다() {

        UUID anotherUserId = UUID.randomUUID();
        String newToken = "existing-refresh-token-" + UUID.randomUUID();

        repository.save(
                userId,
                refreshToken,
                Duration.ofMinutes(10)
        );

        repository.save(
                anotherUserId,
                newToken,
                Duration.ofMinutes(10)
        );

        boolean rotated = repository.rotate(
                userId,
                refreshToken,
                newToken,
                Duration.ofMinutes(5)
        );

        // 새 키가 이미 존재하므로 교체에 실패해야 한다.
        assertThat(rotated).isFalse();

        // 교체 실패로 현재 사용자의 기존 토큰이 삭제되면 안 된다.
        assertThat(repository.findUserId(refreshToken))
                .contains(userId);

        // 이미 등록된 토큰의 소유자가 현재 사용자로 덮어써지면 안 된다.
        assertThat(repository.findUserId(newToken))
                .contains(anotherUserId);


    }

    @Test
    @DisplayName("기존 토큰의 사용자 ID가 다르면 기존 토큰을 유지하고 교체를 거절한다.")
    void 사용자_ID가_다르면_교체를_거절한다() {
        UUID anotherUserId = UUID.randomUUID();
        String newToken = "new-refresh-token-" + UUID.randomUUID();

        repository.save(userId, refreshToken, Duration.ofMinutes(10));

        boolean rotated = repository.rotate(
                anotherUserId,
                refreshToken,
                newToken,
                Duration.ofMinutes(5)
        );

        assertThat(rotated).isFalse();

        // 다른 사용자의 요청으로 정상 토큰이 삭제되면 안 된다.
        assertThat(repository.findUserId(refreshToken)).contains(userId);
        assertThat(repository.findUserId(newToken)).isEmpty();
    }

    @Test
    @DisplayName("같은 Refresh Token을 동시에 교체하면 하나만 성공한다.")
    void 동시에_교체하면_하나만_성공한다() throws Exception {
        String firstNewToken = "first-refresh-token-" + UUID.randomUUID();
        String secondNewToken = "second-refresh-token-" + UUID.randomUUID();
        Duration ttl = Duration.ofMinutes(5);

        repository.save(userId, refreshToken, Duration.ofMinutes(10));

        ExecutorService executor = Executors.newFixedThreadPool(2);

        // 두 작업이 모두 준비된 뒤 교체 요청을 시작한다.
        CyclicBarrier barrier = new CyclicBarrier(2);

        try {
            Future<Boolean> first = executor.submit(()-> {
                barrier.await(5, TimeUnit.SECONDS);

                return repository.rotate(
                        userId,
                        refreshToken,
                        firstNewToken,
                        ttl
                );
            });

            Future<Boolean> second = executor.submit(()-> {
                barrier.await(5, TimeUnit.SECONDS);

                return repository.rotate(
                        userId,
                        refreshToken,
                        secondNewToken,
                        ttl
                );
            });

            boolean firstSuccess = first.get(10, TimeUnit.SECONDS);
            boolean secondSuccess = second.get(10, TimeUnit.SECONDS);

            // 성공 하나, 실패 하나여야 한다.
            assertThat(List.of(firstSuccess, secondSuccess))
                    .containsExactlyInAnyOrder(true, false);

            assertThat(repository.findUserId(refreshToken)).isEmpty();

            String winnerToken = firstSuccess
                    ? firstNewToken
                    : secondNewToken;

            String loserToken = firstSuccess
                    ? secondNewToken
                    : firstNewToken;

            assertThat(repository.findUserId(winnerToken)).contains(userId);
            assertThat(repository.findUserId(loserToken)).isEmpty();
        } finally {
            // 실패하더라도 작업 스레드를 정리한다.
            executor.shutdown();
            executor.awaitTermination(5, TimeUnit.SECONDS);
        }
    }

    @ParameterizedTest
    @ValueSource(longs = {0, -1})
    @DisplayName("TTL이 0 이하이면 저장을 거절한다.")
    void TTL이_0_이하면_저장을_거절한다(long seconds) {
        assertThatThrownBy(() -> repository.save(userId, refreshToken, Duration.ofSeconds(seconds)))
                .isInstanceOf(IllegalArgumentException.class);

        // 유효하지 않은 요청으로 Redis 데이터가 생성되면 안 된다.
        assertThat(redisTemplate.hasKey(redisKey(refreshToken)))
                .isFalse();
    }




    /**
     * 실제 Redis 저장 형식을 검증하기 위한 예상 키를 만든다.
     *
     * 토큰을 UTF-8 바이트로 변환하고 SHA-256으로 해시한 뒤
     * 소문자 16진수 문자열로 표현한다.
     */
    private String redisKey(String token) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(token.getBytes(StandardCharsets.UTF_8));
            return "auth:refresh:" + HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256을 사용할 수 없습니다.", exception);
        }
    }






}
