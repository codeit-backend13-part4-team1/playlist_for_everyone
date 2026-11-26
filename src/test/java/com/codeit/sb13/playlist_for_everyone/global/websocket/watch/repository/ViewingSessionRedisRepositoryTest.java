//package com.codeit.sb13.playlist_for_everyone.global.websocket.watch.repository;
//
//import com.codeit.sb13.playlist_for_everyone.global.websocket.watching.repository.ViewingSessionRedisRepository;
//import com.codeit.sb13.playlist_for_everyone.global.websocket.watching.config.WatcherScriptConfig;
//import lombok.extern.slf4j.Slf4j;
//import org.junit.jupiter.api.*;
//import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
//import org.springframework.data.redis.core.StringRedisTemplate;
//import org.springframework.test.util.ReflectionTestUtils;
//import org.testcontainers.containers.GenericContainer;
//import org.testcontainers.junit.jupiter.Container;
//import org.testcontainers.junit.jupiter.Testcontainers;
//import org.testcontainers.utility.DockerImageName;
//
//import java.time.Duration;
//import java.time.Instant;
//import java.util.List;
//import java.util.Map;
//import java.util.Set;
//import java.util.UUID;
//
//import static org.assertj.core.api.Assertions.*;
//import static com.codeit.sb13.playlist_for_everyone.global.websocket.watching.config.WatchingSessionProperties.*;
//
//
//@Slf4j
//@DisplayName("동시 시청 세션 저장소 테스트")
//@Testcontainers
//public class ViewingSessionRedisRepositoryTest {
//
//    @Container
//    static final GenericContainer<?> redis =
//            new GenericContainer<>(DockerImageName.parse("redis:7.4-alpine"))
//                    .withExposedPorts(6379);
//
//    private LettuceConnectionFactory factory;
//    private StringRedisTemplate template;
//
//    private WatcherScriptConfig redisScriptConfig = new WatcherScriptConfig();
//
//    private ViewingSessionRedisRepository viewingSessionRedisRepository;
//
//    @BeforeEach
//    void setup(){
//        factory = new LettuceConnectionFactory(redis.getHost(), redis.getMappedPort(6379));
//        factory.afterPropertiesSet();
//        template = new StringRedisTemplate(factory);
//        viewingSessionRedisRepository = new ViewingSessionRedisRepository(
//                template,
//                redisScriptConfig.watcherRegisterScript(),
//                redisScriptConfig.watcherUnregisterScript(),
//                redisScriptConfig.watcherRefreshScript(),
//                redisScriptConfig.watcherCountScript(),
//                redisScriptConfig.watcherDeleteWithContentIdScript(),
//                redisScriptConfig.watcherCountMultipleContentsScript(),
//                redisScriptConfig.watcherUnregisterAllScript(),
//                redisScriptConfig.watcherCurrentViewerCountScript()
//        );
//
//        // 테스트용 타임아웃 세팅 ( 3초 )
//        ReflectionTestUtils.setField(viewingSessionRedisRepository, "TTL", Duration.ofSeconds(3));
//
//        log.info("[WatcherRegistryTest] 테스트 세팅 완료");
//    }
//
//    @AfterEach
//    void tearDown() {
//        factory.destroy();
//    }
//
//
//    @Test
//    @DisplayName("같은 콘텐츠에 서로 다른 사용자 두 명이 등록하면 시청자 수는 2명이다")
//    void registerCountsViewers() {
//        String content = newId();
//
//        viewingSessionRedisRepository.register(content, newId(), newId(), "sub-1");
//        viewingSessionRedisRepository.register(content, newId(), newId(), "sub-2");
//
//        assertThat(viewingSessionRedisRepository.getWatcherCount(content)).isEqualTo(2L);
//    }
//
//    @Test
//    @DisplayName("한 세션을 갱신하면 그 세션의 모든 콘텐츠는 유지되고 미갱신 콘텐츠는 0명이 된다")
//    void refreshKeepsOnlyTheRefreshedSessionAlive() throws InterruptedException {
//        String session = newId();
//        String user = newId();
//        String contentA = newId();
//        String contentB = newId();
//        String idleContent = newId();
//
//        viewingSessionRedisRepository.register(contentA, user, session, "sub-a");
//        viewingSessionRedisRepository.register(contentB, user, session, "sub-b");
//        viewingSessionRedisRepository.register(idleContent, newId(), newId(), "sub-c");
//
//        // 미갱신 콘텐츠도 처음에는 1명이어야 만료 전후를 검증할 수 있다.
//        assertThat(viewingSessionRedisRepository.getWatcherCount(contentA)).isEqualTo(1L);
//        assertThat(viewingSessionRedisRepository.getWatcherCount(contentB)).isEqualTo(1L);
//        assertThat(viewingSessionRedisRepository.getWatcherCount(idleContent)).isEqualTo(1L);
//
//        // 3초 TTL을 넘겨 총 5초간 진행한다. 이 동안 시청자 수 조회는 하지 않는다.
//        for (int i = 0; i < 4; i++) {
//            Thread.sleep(1_000);
//            viewingSessionRedisRepository.refresh(session);
//        }
//
//        assertThat(viewingSessionRedisRepository.getWatcherCount(contentA)).isEqualTo(1L);
//        assertThat(viewingSessionRedisRepository.getWatcherCount(contentB)).isEqualTo(1L);
//        assertThat(viewingSessionRedisRepository.getWatcherCount(idleContent)).isZero();
//    }
//
//    @Test
//    @DisplayName("한 세션에서 구독 여러 개를 삭제하면 각 콘텐츠에 남은 시청자 수가 반영된다")
//    void removeSubscriptionsUpdatesEachContent() {
//        String session = newId();
//        String user = newId();
//        String contentA = newId();
//        String contentB = newId();
//
//        viewingSessionRedisRepository.register(contentA, user, session, "sub-a");
//        viewingSessionRedisRepository.register(contentB, user, session, "sub-b");
//
//        // A에는 다른 세션의 시청자 한 명이 더 있다.
//        viewingSessionRedisRepository.register(contentA, newId(), newId(), "sub-c");
//
//        assertThat(viewingSessionRedisRepository.getWatcherCount(contentA)).isEqualTo(2L);
//        assertThat(viewingSessionRedisRepository.getWatcherCount(contentB)).isEqualTo(1L);
//
//        viewingSessionRedisRepository.unregisterAll(session, List.of("sub-a", "sub-b"));
//
//        assertThat(viewingSessionRedisRepository.getWatcherCount(contentA)).isEqualTo(1L);
//        assertThat(viewingSessionRedisRepository.getWatcherCount(contentB)).isZero();
//    }
//
//    @Test
//    @DisplayName("콘텐츠를 삭제한 뒤 같은 세션을 갱신해도 삭제한 콘텐츠는 다시 집계되지 않는다")
//    void removeContentSurvivesSessionRefresh() {
//        String session = newId();
//        String user = newId();
//        String removedContent = newId();
//        String remainingContent = newId();
//
//        viewingSessionRedisRepository.register(removedContent, user, session, "sub-a");
//        viewingSessionRedisRepository.register(remainingContent, user, session, "sub-b");
//
//        assertThat(viewingSessionRedisRepository.getWatcherCount(removedContent)).isEqualTo(1L);
//        assertThat(viewingSessionRedisRepository.getWatcherCount(remainingContent)).isEqualTo(1L);
//
//        viewingSessionRedisRepository.deleteWithContentId(UUID.fromString(removedContent));
//
//        assertThat(viewingSessionRedisRepository.getWatcherCount(removedContent)).isZero();
//
//        viewingSessionRedisRepository.refresh(session);
//
//        assertThat(viewingSessionRedisRepository.getWatcherCount(removedContent)).isZero();
//        assertThat(viewingSessionRedisRepository.getWatcherCount(remainingContent)).isEqualTo(1L);
//    }
//
//    private static String newId() {
//        return UUID.randomUUID().toString();
//    }
//
//
//}
