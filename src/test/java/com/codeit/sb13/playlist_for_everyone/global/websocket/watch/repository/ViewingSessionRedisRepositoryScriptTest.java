package com.codeit.sb13.playlist_for_everyone.global.websocket.watch.repository;

import com.codeit.sb13.playlist_for_everyone.global.websocket.watching.config.WatcherScriptConfig;
import com.codeit.sb13.playlist_for_everyone.global.websocket.watching.dto.ViewingSessionInfoDto;
import com.codeit.sb13.playlist_for_everyone.global.websocket.watching.repository.ViewingSessionRedisRepository;
import org.junit.jupiter.api.*;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static com.codeit.sb13.playlist_for_everyone.global.websocket.watching.config.WatcherScriptConfig.TTL_TIMEOUT;
import static com.codeit.sb13.playlist_for_everyone.global.websocket.watching.config.WatchingSessionProperties.*;
import static org.assertj.core.api.Assertions.assertThat;

@Disabled
class ViewingSessionRedisRepositoryScriptTest {

    private static final String SESSION_STORE = VIEW_SESSION_KEY_PREFIX + "store";
    private static final String SESSION_LIFE = VIEW_SESSION_KEY_PREFIX + "life";
    private static final String ACTIVE_USERS = VIEW_USER_KEY_PREFIX + "active";
    private static final String CONTENT_COUNTER = VIEW_CONTENT_KEY_PREFIX + "counter";

    private final UUID contentA = UUID.randomUUID();
    private final UUID contentB = UUID.randomUUID();
    private final UUID userA = UUID.randomUUID();
    private final UUID userB = UUID.randomUUID();
    private final UUID sessionA = UUID.randomUUID();
    private final UUID sessionB = UUID.randomUUID();
    private final UUID sessionC = UUID.randomUUID();

    private LettuceConnectionFactory factory;
    private StringRedisTemplate redis;
    private ViewingSessionRedisRepository repository;

    @BeforeEach
    void setUp() {
        RedisStandaloneConfiguration config = new RedisStandaloneConfiguration(
                System.getProperty("redis.test.host", "localhost"),
                Integer.getInteger("redis.test.port", 6379)
        );
        config.setDatabase(Integer.getInteger("redis.test.database", 15));

        factory = new LettuceConnectionFactory(config);
        factory.afterPropertiesSet();
        redis = new StringRedisTemplate(factory);

        WatcherScriptConfig scripts = new WatcherScriptConfig();
        repository = new ViewingSessionRedisRepository(
                redis,
                scripts.watcherRegisterScript(),
                scripts.watcherUnregisterScript(),
                scripts.watcherRefreshScript(),
                scripts.watcherCountScript(),
                scripts.watcherDeleteWithContentIdScript(),
                scripts.watcherCountMultipleContentsScript(),
                scripts.watcherUnregisterAllScript(),
                scripts.watcherCurrentViewerCountScript()
        );
    }

    @AfterEach
    void tearDown() {
        try {
            if (redis != null) {
                String[] sessionIds = {
                        sessionA.toString(), sessionB.toString(), sessionC.toString()
                };
                redis.opsForHash().delete(SESSION_STORE, (Object[]) sessionIds);
                redis.opsForZSet().remove(SESSION_LIFE, (Object[]) sessionIds);
                redis.opsForHash().delete(ACTIVE_USERS, userA.toString(), userB.toString());
                redis.opsForHash().delete(CONTENT_COUNTER, contentA.toString(), contentB.toString());
                redis.delete(List.of(
                        userIndex(userA), userIndex(userB),
                        contentIndex(contentA), contentIndex(contentB)
                ));
            }
        } finally {
            if (factory != null) {
                factory.destroy();
            }
        }
    }

    @Test
    @DisplayName("같은 사용자의 여러 세션은 시청자 한 명으로 집계한다")
    void registerCountsDistinctUsers() {
        assertThat(register(sessionA, contentA, userA)).isTrue();
        assertThat(register(sessionB, contentA, userA)).isFalse();
        assertThat(register(sessionC, contentA, userB)).isTrue();

        assertThat(repository.getWatcherCount(contentA.toString())).isEqualTo(2L);
    }

    @Test
    @DisplayName("한 세션을 취소해도 같은 사용자의 다른 세션은 유지한다")
    void unregisterKeepsRemainingSession() {
        register(sessionA, contentA, userA);
        register(sessionB, contentA, userA);

        repository.unregister(sessionA);

        assertSessionRemoved(sessionA, contentA, userA);
        assertThat(repository.getWatcherCount(contentA.toString())).isEqualTo(1L);
        assertThat(redis.opsForHash().get(ACTIVE_USERS, userA.toString()))
                .isEqualTo(sessionB.toString());
    }

    @Test
    @DisplayName("일괄 취소에서 없는 세션을 건너뛰고 나머지 대상은 삭제한다")
    void unregisterAllSkipsMissingSession() {
        register(sessionA, contentA, userA);
        register(sessionB, contentB, userA);
        register(sessionC, contentA, userB);

        repository.unregisterAll(List.of(UUID.randomUUID(), sessionA, sessionB));

        assertSessionRemoved(sessionA, contentA, userA);
        assertSessionRemoved(sessionB, contentB, userA);
        assertThat(repository.getWatcherCount(contentA.toString())).isEqualTo(1L);
        assertThat(repository.getWatcherCount(contentB.toString())).isEqualTo(0L);
    }

    @Test
    @DisplayName("수명 갱신은 중앙 만료시간과 사용자·콘텐츠 인덱스에 반영된다")
    void refreshUpdatesAllExpirationScores() {
        register(sessionA, contentA, userA);
        String sessionId = sessionA.toString();

        Double originalExpiry = redis.opsForZSet().score(SESSION_LIFE, sessionId);
        assertThat(originalExpiry).isNotNull();

        // 활성 상태를 유지하면서 기존 점수를 앞당겨, 대기 없이 갱신 여부를 확인한다.
        double earlierExpiry = originalExpiry - Duration.ofSeconds(TTL_TIMEOUT).toMillis() / 2.0;
        redis.opsForZSet().add(SESSION_LIFE, sessionId, earlierExpiry);
        redis.opsForZSet().add(userIndex(userA), sessionId, earlierExpiry);
        redis.opsForZSet().add(contentIndex(contentA), sessionId, earlierExpiry);

        repository.refresh(List.of(sessionA));

        Double refreshedExpiry = redis.opsForZSet().score(SESSION_LIFE, sessionId);
        assertThat(refreshedExpiry).isGreaterThan(earlierExpiry);
        assertThat(redis.opsForZSet().score(userIndex(userA), sessionId))
                .isEqualTo(refreshedExpiry);
        assertThat(redis.opsForZSet().score(contentIndex(contentA), sessionId))
                .isEqualTo(refreshedExpiry);
    }

    @Test
    @DisplayName("콘텐츠 삭제는 대상의 세션·인덱스·카운터를 지우고 다른 콘텐츠는 유지한다")
    void deleteWithContentIdKeepsOtherContent() {
        register(sessionA, contentA, userA);
        register(sessionB, contentA, userB);
        register(sessionC, contentB, userA);

        repository.deleteWithContentId(contentA);

        assertSessionRemoved(sessionA, contentA, userA);
        assertSessionRemoved(sessionB, contentA, userB);
        assertThat(redis.hasKey(contentIndex(contentA))).isFalse();
        assertThat(redis.opsForHash().hasKey(CONTENT_COUNTER, contentA.toString())).isFalse();
        assertThat(repository.getWatcherCount(contentB.toString())).isEqualTo(1L);
        assertThat(redis.opsForHash().get(ACTIVE_USERS, userA.toString()))
                .isEqualTo(sessionC.toString());
    }

    @Test
    @DisplayName("여러 콘텐츠의 시청자 수를 조회하고 없는 콘텐츠는 0을 반환한다")
    void countMultipleContentsIncludesMissingContent() {
        register(sessionA, contentA, userA);
        register(sessionB, contentA, userB);
        register(sessionC, contentB, userA);
        UUID missingContent = UUID.randomUUID();

        Map<UUID, Long> result = repository.getWatcherCountWithMultipleContents(
                List.of(contentA.toString(), contentB.toString(), missingContent.toString())
        );

        assertThat(result).containsExactlyInAnyOrderEntriesOf(
                Map.of(contentA, 2L, contentB, 1L, missingContent, 0L)
        );
        assertThat(repository.getWatcherCountWithMultipleContents(List.of())).isEmpty();
    }

    @Test
    @DisplayName("현재 시청자 조회는 대상 콘텐츠의 활성 세션 정보를 반환한다")
    void currentViewerIdListReturnsActiveSessionForRequestedContent() {
        Instant ctime = Instant.now();
        repository.register(sessionA, contentA, userA, ctime);
        register(sessionB, contentB, userB);

        assertThat(repository.currentViewerIdList(contentA.toString()))
                .usingRecursiveFieldByFieldElementComparator()
                .containsExactly(new ViewingSessionInfoDto(
                        sessionA,
                        ctime,
                        contentA,
                        userA
                ));
    }

    private boolean register(UUID sessionId, UUID contentId, UUID userId) {
        return repository.register(sessionId, contentId, userId, Instant.now());
    }

    private void assertSessionRemoved(UUID sessionId, UUID contentId, UUID userId) {
        String id = sessionId.toString();
        assertThat(redis.opsForHash().hasKey(SESSION_STORE, id)).isFalse();
        assertThat(redis.opsForZSet().score(SESSION_LIFE, id)).isNull();
        assertThat(redis.opsForZSet().score(userIndex(userId), id)).isNull();
        assertThat(redis.opsForZSet().score(contentIndex(contentId), id)).isNull();
    }

    private String userIndex(UUID userId) {
        return VIEW_USER_KEY_PREFIX + userId;
    }

    private String contentIndex(UUID contentId) {
        return VIEW_CONTENT_KEY_PREFIX + contentId;
    }
}