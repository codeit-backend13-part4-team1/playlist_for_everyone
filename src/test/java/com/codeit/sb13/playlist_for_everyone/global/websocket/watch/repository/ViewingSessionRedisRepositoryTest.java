package com.codeit.sb13.playlist_for_everyone.global.websocket.watch.repository;

import com.codeit.sb13.playlist_for_everyone.global.websocket.watching.config.WatcherScriptConfig;
import com.codeit.sb13.playlist_for_everyone.global.websocket.watching.repository.ViewingSessionRedisRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static com.codeit.sb13.playlist_for_everyone.global.websocket.watching.config.WatchingSessionProperties.*;
import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("동시 시청 세션 저장소 테스트")
@Testcontainers
public class ViewingSessionRedisRepositoryTest {

    @Container
    static final GenericContainer<?> redis =
            new GenericContainer<>(DockerImageName.parse("redis:7.4-alpine"))
                    .withExposedPorts(6379);

    private static final String SESSION_STORE = VIEW_SESSION_KEY_PREFIX + "store";
    private static final String SESSION_LIFE = VIEW_SESSION_KEY_PREFIX + "life";
    private static final String ACTIVE_USERS = VIEW_USER_KEY_PREFIX + "active";
    private static final String CONTENT_COUNTER = VIEW_CONTENT_KEY_PREFIX + "counter";
    private static final long TTL_TIMEOUT = 10;

    private final UUID contentA = UUID.randomUUID();
    private final UUID contentB = UUID.randomUUID();
    private final UUID userA = UUID.randomUUID();
    private final UUID userB = UUID.randomUUID();
    private final UUID sessionA = UUID.randomUUID();
    private final UUID sessionB = UUID.randomUUID();
    private final UUID sessionC = UUID.randomUUID();

    private LettuceConnectionFactory factory;
    private StringRedisTemplate template;
    private ViewingSessionRedisRepository repository;

    @BeforeEach
    void setUp() {
        factory = new LettuceConnectionFactory(redis.getHost(), redis.getMappedPort(6379));
        factory.afterPropertiesSet();
        template = new StringRedisTemplate(factory);

        WatcherScriptConfig scripts = new WatcherScriptConfig();
        repository = new ViewingSessionRedisRepository(
                template,
                scripts.watcherRegisterScript(),
                scripts.watcherUnregisterScript(),
                scripts.watcherRefreshScript(),
                scripts.watcherCountScript(),
                scripts.watcherDeleteWithContentIdScript(),
                scripts.watcherCountMultipleContentsScript(),
                scripts.watcherUnregisterAllScript(),
                scripts.watcherCurrentViewerCountScript(),
                scripts.watcherQueryViewerScript()
        );
    }

    @AfterEach
    void tearDown() {
        try {
            if (template != null) {
                Object[] sessionIds = {
                        sessionA.toString(), sessionB.toString(), sessionC.toString()
                };

                template.opsForHash().delete(SESSION_STORE, sessionIds);
                template.opsForZSet().remove(SESSION_LIFE, sessionIds);
                template.opsForHash().delete(ACTIVE_USERS, userA.toString(), userB.toString());
                template.opsForHash().delete(CONTENT_COUNTER, contentA.toString(), contentB.toString());
                template.delete(List.of(
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
    @DisplayName("같은 사용자의 중복 구독은 시청자 한 명으로 집계한다")
    void registerCountsDistinctViewers() {
        assertThat(register(sessionA, contentA, userA)).isTrue();
        assertThat(register(sessionB, contentA, userA)).isFalse();
        assertThat(register(sessionC, contentA, userB)).isTrue();

        assertThat(repository.getWatcherCount(contentA.toString())).isEqualTo(2L);
    }

    @Test
    @DisplayName("활성 세션을 취소하면 같은 사용자의 남은 세션을 활성화한다")
    void unregisterReplacesActiveSession() {
        register(sessionA, contentA, userA);
        register(sessionB, contentA, userA);
        template.opsForHash().put(ACTIVE_USERS, userA.toString(), sessionA.toString());

        repository.unregister(sessionA);

        assertSessionRemoved(sessionA, contentA, userA);
        assertThat(repository.getWatcherCount(contentA.toString())).isEqualTo(1L);
        assertThat(template.opsForHash().get(ACTIVE_USERS, userA.toString()))
                .isEqualTo(sessionB.toString());
    }

    @Test
    @DisplayName("일괄 취소는 중간에 없는 세션이 있어도 앞뒤 세션을 모두 삭제한다")
    void unregisterAllSkipsMissingSession() {
        register(sessionA, contentA, userA);
        register(sessionB, contentB, userA);
        register(sessionC, contentA, userB);

        repository.unregisterAll(List.of(sessionA, UUID.randomUUID(), sessionB));

        assertSessionRemoved(sessionA, contentA, userA);
        assertSessionRemoved(sessionB, contentB, userA);
        assertThat(repository.getWatcherCount(contentA.toString())).isEqualTo(1L);
        assertThat(repository.getWatcherCount(contentB.toString())).isEqualTo(0L);
    }

    @Test
    @DisplayName("수명 갱신은 중앙 만료시간과 사용자·콘텐츠 인덱스에 반영된다")
    void refreshUpdatesAllExpirationScores() {
        register(sessionA, contentA, userA);
        register(sessionB, contentB, userB);

        String sessionId = sessionA.toString();
        Double originalExpiry = template.opsForZSet().score(SESSION_LIFE, sessionId);
        Double untouchedExpiry = template.opsForZSet().score(SESSION_LIFE, sessionB.toString());
        assertThat(originalExpiry).isNotNull();
        assertThat(untouchedExpiry).isNotNull();

        double earlierExpiry = originalExpiry - Duration.ofSeconds(TTL_TIMEOUT).toMillis() / 2.0;
        template.opsForZSet().add(SESSION_LIFE, sessionId, earlierExpiry);
        template.opsForZSet().add(userIndex(userA), sessionId, earlierExpiry);
        template.opsForZSet().add(contentIndex(contentA), sessionId, earlierExpiry);

        repository.refresh(List.of(sessionA));

        Double refreshedExpiry = template.opsForZSet().score(SESSION_LIFE, sessionId);
        assertThat(refreshedExpiry).isGreaterThan(earlierExpiry);
        assertThat(template.opsForZSet().score(userIndex(userA), sessionId))
                .isEqualTo(refreshedExpiry);
        assertThat(template.opsForZSet().score(contentIndex(contentA), sessionId))
                .isEqualTo(refreshedExpiry);
        assertThat(template.opsForZSet().score(SESSION_LIFE, sessionB.toString()))
                .isEqualTo(untouchedExpiry);
    }

    @Test
    @DisplayName("콘텐츠 전체 삭제는 다른 콘텐츠를 보존하고 갱신해도 삭제한 세션은 복구되지 않는다")
    void deleteWithContentIdKeepsOtherContent() {
        register(sessionA, contentA, userA);
        register(sessionB, contentA, userB);
        register(sessionC, contentB, userA);

        assertThat(template.opsForHash().hasKey(CONTENT_COUNTER, contentA.toString()))
                .isTrue();

        repository.deleteWithContentId(contentA);

        assertSessionRemoved(sessionA, contentA, userA);
        assertSessionRemoved(sessionB, contentA, userB);
        assertThat(template.hasKey(contentIndex(contentA))).isFalse();
        assertThat(template.opsForHash().hasKey(CONTENT_COUNTER, contentA.toString()))
                .isFalse();

        repository.refresh(List.of(sessionA, sessionC));

        assertSessionRemoved(sessionA, contentA, userA);
        assertThat(repository.getWatcherCount(contentA.toString())).isEqualTo(0L);
        assertThat(repository.getWatcherCount(contentB.toString())).isEqualTo(1L);
    }

    @Test
    @DisplayName("여러 콘텐츠의 시청자 수를 조회하고 없는 콘텐츠는 0명을 반환한다")
    void countMultipleContentsIncludesMissingContent() {
        register(sessionA, contentA, userA);
        register(sessionB, contentA, userB);
        register(sessionC, contentB, userA);
        UUID missingContent = UUID.randomUUID();

        Map<UUID, Long> counts = repository.getWatcherCountWithMultipleContents(
                List.of(contentA.toString(), contentB.toString(), missingContent.toString())
        );

        assertThat(counts).containsExactlyInAnyOrderEntriesOf(
                Map.of(contentA, 2L, contentB, 1L, missingContent, 0L)
        );
        assertThat(repository.getWatcherCountWithMultipleContents(List.of())).isEmpty();
    }

    @Test
    @DisplayName("현재 시청자 조회는 만료 세션을 제외하고 대상 콘텐츠의 활성 세션을 저장해 반환한다")
    void currentViewerListRepairsActiveSessionAndSkipsExpiredSession() {
        Instant createdAt = Instant.now();
        repository.register(sessionA, contentA, userA, createdAt);
        register(sessionB, contentB, userA);
        register(sessionC, contentA, userB);

        // 다른 콘텐츠의 세션이 현재 활성 세션인 상황을 만든다.
        template.opsForHash().put(ACTIVE_USERS, userA.toString(), sessionB.toString());

        // 콘텐츠 인덱스에는 세션이 남아 있지만 중앙 수명은 만료된 상황을 만든다.
        template.opsForZSet().add(SESSION_LIFE, sessionC.toString(), 0.0);

        assertThat(repository.currentViewerIdList(contentA.toString()))
                .singleElement()
                .satisfies(info -> {
                    assertThat(info.subscriptionId()).isEqualTo(sessionA);
                    assertThat(info.createdAt()).isEqualTo(createdAt);
                    assertThat(info.userId()).isEqualTo(userA);
                    assertThat(info.contentId()).isEqualTo(contentA);
                });

        assertThat(template.opsForHash().get(ACTIVE_USERS, userA.toString()))
                .isEqualTo(sessionA.toString());
    }

    @Test
    @DisplayName("사용자 ID로 활성 세션의 정보 네 필드를 조회한다")
    void queryViewerIdReturnsActiveSessionInfo() {
        Instant createdAt = Instant.now();
        repository.register(sessionA, contentA, userA, createdAt);

        assertThat(repository.queryViewerId(userA.toString()))
                .hasValueSatisfying(info -> {
                    assertThat(info.subscriptionId()).isEqualTo(sessionA);
                    assertThat(info.createdAt()).isEqualTo(createdAt);
                    assertThat(info.userId()).isEqualTo(userA);
                    assertThat(info.contentId()).isEqualTo(contentA);
                });
    }

    @Test
    @DisplayName("활성 세션이 없는 사용자를 조회하면 빈 결과를 반환한다")
    void queryViewerIdReturnsEmptyForUserWithoutActiveSession() {
        register(sessionA, contentA, userA);

        assertThat(repository.queryViewerId(userB.toString())).isEmpty();
    }

    @Test
    @DisplayName("활성 세션의 중앙 수명이 만료되면 빈 결과를 반환한다")
    void queryViewerIdReturnsEmptyForExpiredActiveSession() {
        register(sessionA, contentA, userA);
        assertThat(repository.queryViewerId(userA.toString())).isPresent();

        template.opsForZSet().add(SESSION_LIFE, sessionA.toString(), 0.0);

        assertThat(repository.queryViewerId(userA.toString())).isEmpty();
    }

    private boolean register(UUID sessionId, UUID contentId, UUID userId) {
        return repository.register(sessionId, contentId, userId, Instant.now());
    }

    private void assertSessionRemoved(UUID sessionId, UUID contentId, UUID userId) {
        String id = sessionId.toString();

        assertThat(template.opsForHash().hasKey(SESSION_STORE, id)).isFalse();
        assertThat(template.opsForZSet().score(SESSION_LIFE, id)).isNull();
        assertThat(template.opsForZSet().score(userIndex(userId), id)).isNull();
        assertThat(template.opsForZSet().score(contentIndex(contentId), id)).isNull();
    }

    private static String userIndex(UUID userId) {
        return VIEW_USER_KEY_PREFIX + userId;
    }

    private static String contentIndex(UUID contentId) {
        return VIEW_CONTENT_KEY_PREFIX + contentId;
    }
}
