package com.codeit.sb13.playlist_for_everyone.domain.websocket.watch;

import com.codeit.sb13.playlist_for_everyone.global.service.WatcherRegistry;
import com.codeit.sb13.playlist_for_everyone.global.websocket.WatcherScriptConfig;
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
import static com.codeit.sb13.playlist_for_everyone.global.config.RedisConfig.*;


@Slf4j
@DisplayName("동시 시청 세션 저장소 테스트")
@Testcontainers
public class WatcherRegistryTest {

    @Container
    static final GenericContainer<?> redis =
            new GenericContainer<>(DockerImageName.parse("redis:7.4-alpine"))
                    .withExposedPorts(6379);

    private LettuceConnectionFactory factory;
    private StringRedisTemplate template;

    private WatcherScriptConfig redisScriptConfig = new WatcherScriptConfig();

    private WatcherRegistry watcherRegistry;

    @BeforeEach
    void setup(){
        factory = new LettuceConnectionFactory(redis.getHost(), redis.getMappedPort(6379));
        factory.afterPropertiesSet();
        template = new StringRedisTemplate(factory);
        watcherRegistry = new WatcherRegistry(
                template,
                redisScriptConfig.watcherRegisterScript(),
                redisScriptConfig.watcherUnregisterScript(),
                redisScriptConfig.watcherRefreshScript(),
                redisScriptConfig.watcherDeleteScript(),
                redisScriptConfig.watcherCountScript()
        );

        // 테스트용 타임아웃 세팅
        ReflectionTestUtils.setField(watcherRegistry, "TTL", Duration.ofSeconds(3));

        log.info("[WatcherRegistryTest] 테스트 세팅 완료");
    }

    @AfterEach
    void teardown(){
        clear();
        factory.destroy();
        log.info("[WatcherRegistryTest] 테스트 종료");
    }

    private void clear(){
        Set<String> watchers = template.keys("watcher:*");
        Set<String> sessions = template.keys("websocket:*");
        watchers.addAll(sessions);
        for(String key : watchers){
            template.delete(key);
            log.info("[WatcherRegistryTest] key 삭제됨 - {}",key);
        }
    }

    private boolean checkContainedChannelIdInList(String sessionId, String targetId){
         Map<Object,Object> test= template.opsForHash()
                 .entries(WEBSOCKET_SESSION_PREFIX + sessionId);

         for(Object id : test.values()){
             log.info("[WatcherRegistryTest] id 검색됨 - {}",id);
             if (targetId.equals(id)) return true;
         }
        return false;
    }
    private boolean checkContainedSessionIdInList(String contentId, String targetId){
        Set<String> idList = template.opsForZSet()
                .range(WATCHER_CONTENT_PREFIX + contentId,0,-1);
        for (String id : idList) {
            log.info("[WatcherRegistryTest] id 검색됨 - {}",id);
            if (targetId.equals(id)) return true;
        }
        return false;
    }

    private String subscriptionKey(String session, String subscription){
        return session + "-" + subscription;
    }


    @Test
    @DisplayName("컨텐츠와 현재세션번호를 기반으로 시청 등록 후 세션 id 기반 삭제 테스트")
    void registerWatcher(){
        String contentId = UUID.randomUUID().toString();
        String sessionId = "a56s72D43D";
        String subscriptionId = "sub-1";

        watcherRegistry.register(sessionId, subscriptionId, contentId);

        assertThat(checkContainedChannelIdInList(sessionId, contentId)).isTrue();
        assertThat(checkContainedSessionIdInList(contentId, subscriptionKey(sessionId,subscriptionId))).isTrue();

        watcherRegistry.unregister(sessionId, subscriptionId);

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

        watcherRegistry.register(session1, subscriptionId1, contentId);
        watcherRegistry.register(session2, subscriptionId2, contentId);
        watcherRegistry.register(session3, subscriptionId3, contentId);


        log.info("[WatcherRegistryTest] 세션 수 조회 - {}", watcherRegistry.getWatcherCount(contentId));
        assertThat(watcherRegistry.getWatcherCount(contentId)).isEqualTo(3L);
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

        watcherRegistry.register(session1, subscription1, content1);
        watcherRegistry.register(session2, subscription2, content2);

        log.info("[WatcherRegistryTest] 세션 등록 완료");

        Instant start = Instant.now();
        Instant now = Instant.now();
        Duration duration = Duration.between(start, now);

        do {
            try {
                Thread.sleep(1000);
                watcherRegistry.refresh(session1);
                log.info("[WatcherRegistryTest] 컨텐츠 갱신 완료");
                now = Instant.now();
                duration = Duration.between(start, now);
            }
            catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.info("[WatcherRegistryTest] 컨텐츠 갱신 중 인터럽트 발생");
            }
        } while (duration.toSeconds() < 5);

        assertThat(watcherRegistry.getWatcherCount(content1)).isEqualTo(1L);
        assertThat(watcherRegistry.getWatcherCount(content2)).isEqualTo(0L);

    }



    @Test
    @DisplayName("세션 구독 전부삭제 테스트")
    void deleteTest(){
        String content = UUID.randomUUID().toString();
        String session = "sdfsdf";
        String subscribe = "sub-1";

        watcherRegistry.register(session, subscribe, content);

        watcherRegistry.delete(session);

        assertThat(watcherRegistry.getWatcherCount(content)).isEqualTo(0L);
        assertThat(checkContainedChannelIdInList(session,content)).isFalse();
    }


}
