package com.codeit.sb13.playlist_for_everyone.global.websocket.watch.repository;

import com.codeit.sb13.playlist_for_everyone.global.websocket.watching.repository.WatchingSessionRepository;
import com.codeit.sb13.playlist_for_everyone.global.websocket.watching.config.WatcherScriptConfig;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.*;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.util.ReflectionTestUtils;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static com.codeit.sb13.playlist_for_everyone.global.websocket.watching.config.WatchingSessionProperties.*;


@Slf4j
@DisplayName("동시 시청 세션 저장소 테스트")
@Testcontainers
public class WatchingSessionRepositoryTest {

    @Container
    static final GenericContainer<?> redis =
            new GenericContainer<>(DockerImageName.parse("redis:7.4-alpine"))
                    .withExposedPorts(6379);

    private LettuceConnectionFactory factory;
    private StringRedisTemplate template;

    private WatcherScriptConfig redisScriptConfig = new WatcherScriptConfig();

    private WatchingSessionRepository watchingSessionRepository;

    @BeforeEach
    void setup(){
        factory = new LettuceConnectionFactory(redis.getHost(), redis.getMappedPort(6379));
        factory.afterPropertiesSet();
        template = new StringRedisTemplate(factory);
        watchingSessionRepository = new WatchingSessionRepository(
                template,
                redisScriptConfig.watcherRegisterScript(),
                redisScriptConfig.watcherUnregisterScript(),
                redisScriptConfig.watcherRefreshScript(),
                redisScriptConfig.watcherDeleteScript(),
                redisScriptConfig.watcherCountScript(),
                redisScriptConfig.watcherDeleteWithContentIdScript()
        );

        // 테스트용 타임아웃 세팅 ( 3초 )
        ReflectionTestUtils.setField(watchingSessionRepository, "TTL", Duration.ofSeconds(3));

        log.info("[WatcherRegistryTest] 테스트 세팅 완료");
    }

    @AfterEach
    void teardown(){
        clear();
        factory.destroy();
        log.info("[WatcherRegistryTest] 테스트 종료");
    }

    private void clear(){
        Set<String> sessions = template.keys(STORAGE_SESSION_PREFIX + "*");
        Set<String> contents = template.keys(STORAGE_CONTENT_PREFIX + "*");
        contents.addAll(sessions);
        for(String key : contents){
            template.delete(key);
            log.info("[WatcherRegistryTest] key 삭제됨 - {}",key);
        }
    }

    private boolean checkContainedChannelIdInList(String sessionId, String targetId){
         Map<Object,Object> test= template.opsForHash()
                 .entries(STORAGE_SESSION_PREFIX + sessionId);

         log.info("세션 id:{} - {}",sessionId,test);

         for(Object id : test.values()){
             log.info("[WatcherRegistryTest] id 검색됨 - {}",id);
             if (targetId.equals(id)) return true;
         }
        return false;
    }
    private boolean checkContainedSessionIdInList(String contentId, String targetId){
        Set<String> idList = template.opsForZSet()
                .range(STORAGE_CONTENT_PREFIX + contentId,0,-1);
        for (String id : idList) {
            log.info("[WatcherRegistryTest] id 검색됨 - {}",id);
            if (targetId.equals(id)) return true;
        }
        return false;
    }

    private String subscriptionKey(String session, String subscription){
        return session + WatcherScriptConfig.DELIMITER + subscription;
    }


    @Test
    @DisplayName("컨텐츠와 현재세션번호를 기반으로 시청 등록 후 세션 id 기반 삭제 테스트")
    void registerWatcher(){
        String contentId = UUID.randomUUID().toString();
        String sessionId = "a56s72D43D";
        String subscriptionId = "sub-1";

        watchingSessionRepository.register(sessionId, subscriptionId, contentId);

        assertThat(checkContainedChannelIdInList(sessionId, contentId)).isTrue();
        assertThat(checkContainedSessionIdInList(contentId, subscriptionKey(sessionId,subscriptionId))).isTrue();

        watchingSessionRepository.unregister(sessionId, subscriptionId);

        assertThat(checkContainedChannelIdInList(sessionId, contentId)).isFalse();
        assertThat(checkContainedSessionIdInList(contentId, subscriptionKey(sessionId,subscriptionId))).isFalse();
    }


    @Test
    @DisplayName("현재 컨텐츠를 시청하고 있는 사용자(세션) 수 조회")
    void unRegisterWatcher(){

        String contentId = UUID.randomUUID().toString();
        String session1 = "a56s72D9eD";
        String subscriptionId1 = "sub-1";
        String session2 = "a56172D9eD";
        String subscriptionId2 = "sub-2";
        String session3 = "af1s72D9eD";
        String subscriptionId3 = "sub-3";

        watchingSessionRepository.register(session1, subscriptionId1, contentId);
        watchingSessionRepository.register(session2, subscriptionId2, contentId);
        watchingSessionRepository.register(session3, subscriptionId3, contentId);


        log.info("[WatcherRegistryTest] 세션 수 조회 - {}", watchingSessionRepository.getWatcherCount(contentId));
        assertThat(watchingSessionRepository.getWatcherCount(contentId)).isEqualTo(3L);
    }

    @Test
    @DisplayName("컨텐츠 ttl 갱신 테스트")
    void refreshTest(){
        String content1 = UUID.randomUUID().toString();
        String content2 = UUID.randomUUID().toString();

        String session1 = "asfjkel1";
        String subscription1 = "sub-1";
        String session2 = "asdhfjsdfhkw";
        String subscription2 = "sub-2";

        watchingSessionRepository.register(session1, subscription1, content1);
        watchingSessionRepository.register(session2, subscription2, content2);

        log.info("[WatcherRegistryTest] 세션 등록 완료");

        Instant start = Instant.now();
        Instant now = Instant.now();
        Duration duration = Duration.between(start, now);

        do {
            try {
                Thread.sleep(1000);
                watchingSessionRepository.refresh(session1);
                log.info("[WatcherRegistryTest] 컨텐츠 갱신 완료");
                now = Instant.now();
                duration = Duration.between(start, now);
            }
            catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.info("[WatcherRegistryTest] 컨텐츠 갱신 중 인터럽트 발생");
            }
        } while (duration.toSeconds() < 5);

        assertThat(watchingSessionRepository.getWatcherCount(content1)).isEqualTo(1L);
        assertThat(watchingSessionRepository.getWatcherCount(content2)).isEqualTo(0L);

    }

    @Nested
    @DisplayName("Delete Test")
    class Delete {

        String contentId1 = UUID.randomUUID().toString();
        String contentId2 = UUID.randomUUID().toString();
        String sessionId1 = "test-session-id1";
        String sessionId2 = "test-session-id2";
        String subscriptionId1 = "test-sub-1";
        String subscriptionId2 = "test-sub-2";

        @BeforeEach
        void setUp(){
            // content 1 에는 session 1,2
            watchingSessionRepository.register(sessionId1, subscriptionId1, contentId1);
            watchingSessionRepository.register(sessionId2, subscriptionId1, contentId1);

            // content 2 에는 session 1 만
            watchingSessionRepository.register(sessionId1, subscriptionId2, contentId2);
        }

        @Test
        @DisplayName("세션 구독 전부삭제 테스트")
        void deleteTest(){
            watchingSessionRepository.delete(sessionId1);

            assertThat(watchingSessionRepository.getWatcherCount(contentId1)).isEqualTo(1L);
            assertThat(watchingSessionRepository.getWatcherCount(contentId2)).isEqualTo(0L);
            assertThat(checkContainedChannelIdInList(sessionId1,contentId1)).isFalse();
            assertThat(checkContainedChannelIdInList(sessionId2,contentId2)).isFalse();
        }

        @Test
        @DisplayName("컨텐츠 id 기반 삭제 테스트")
        void deleteWithContentIdTest(){
            watchingSessionRepository.deleteWithContentId(UUID.fromString(contentId1));

            assertThat(watchingSessionRepository.getWatcherCount(contentId1)).isEqualTo(0L);
            assertThat(watchingSessionRepository.getWatcherCount(contentId2)).isEqualTo(1L);
            assertThat(checkContainedChannelIdInList(sessionId1,contentId1)).isFalse();
            assertThat(checkContainedChannelIdInList(sessionId2,contentId1)).isFalse();
            assertThat(checkContainedChannelIdInList(sessionId1,contentId2)).isTrue();
        }
    }




}
