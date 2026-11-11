package com.codeit.sb13.playlist_for_everyone.global.websocket.watch.repository;


import com.codeit.sb13.playlist_for_everyone.global.websocket.watching.config.WatcherScriptConfig;
import com.codeit.sb13.playlist_for_everyone.global.websocket.watching.repository.WatchingSessionRepository;
import org.junit.jupiter.api.*;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static com.codeit.sb13.playlist_for_everyone.global.websocket.watching.config.WatchingSessionProperties.STORAGE_CONTENT_PREFIX;
import static com.codeit.sb13.playlist_for_everyone.global.websocket.watching.config.WatchingSessionProperties.STORAGE_SESSION_PREFIX;
import static org.assertj.core.api.Assertions.assertThat;

//@Disabled // 스크립트 로그 테스트 용. 필요시 활성화.
@Import(WatcherScriptConfig.class)
public class WatchingSessionRepositoryPRT {

    LettuceConnectionFactory factory;
    StringRedisTemplate template;

    WatchingSessionRepository repository;
    WatcherScriptConfig script;

    @BeforeEach
    void setup(){
        RedisStandaloneConfiguration configuration = new RedisStandaloneConfiguration();
        configuration.setHostName("localhost");
        configuration.setPort(6379);

        factory = new LettuceConnectionFactory(configuration);
        factory.afterPropertiesSet();

        template = new StringRedisTemplate(factory);

        script = new WatcherScriptConfig();

        repository = new WatchingSessionRepository(
                template,
                script.watcherRegisterScript(),
                script.watcherUnregisterScript(),
                script.watcherRefreshScript(),
                script.watcherDeleteScript(),
                script.watcherCountScript(),
                script.watcherDeleteWithContentIdScript(),
                script.watcherCountMultipleContentsScript()
        );

        register();
    }

    String contentId1 = UUID.randomUUID().toString();
    String contentId2 = UUID.randomUUID().toString();
    String sessionId1 = "test-session-id1";
    String sessionId2 = "test-session-id2";
    String subscriptionId1 = "test-sub-1";
    String subscriptionId2 = "test-sub-2";

    void register(){
        // content 1 에는 session 1,2
        repository.register(sessionId1, subscriptionId1, contentId1);
        repository.register(sessionId2, subscriptionId1, contentId1);

        // content 2 에는 session 1 만
        repository.register(sessionId1, subscriptionId2, contentId2);
    }


    @AfterEach
    void clean(){
        clear();
        factory.destroy();
    }

    private void clear(){
        Set<String> sessions = template.keys(STORAGE_SESSION_PREFIX + "*");
        Set<String> contents = template.keys(STORAGE_CONTENT_PREFIX + "*");
        contents.addAll(sessions);
        for(String key : contents){
            template.delete(key);
        }
    }

    @Test
    @DisplayName("컨텐츠 id 기반 삭제 테스트")
    void deleteWithContentIdTest(){
        repository.deleteWithContentId(UUID.fromString(contentId1));

        assertThat(repository.getWatcherCount(contentId1)).isEqualTo(0L);
        assertThat(repository.getWatcherCount(contentId2)).isEqualTo(1L);
    }

    @Nested
    @DisplayName("redis 스크립트 조회 테스트")
    class Query {

        String content1 = UUID.randomUUID().toString();
        String content2 = UUID.randomUUID().toString();
        String content3 = UUID.randomUUID().toString();

        String sessionId1 = "session1";
        String sessionId2 = "session2";
        String sessionId3 = "session3";

        String subscriptionId1 = "subscription1";
        String subscriptionId2 = "subscription2";
        String subscriptionId3 = "subscription3";

        @BeforeEach
        void setUp(){
            List<String> contentIdList = List.of(content1,content2,content3);
            List<String> sessionIdList = List.of(sessionId1,sessionId2,sessionId3);
            List<String> subscriptionIdList = List.of(subscriptionId1,subscriptionId2,subscriptionId3);


            for (String subscription: subscriptionIdList){
                repository.register(sessionId1, subscription, content1);
            }

            for (String subscription: subscriptionIdList){
                repository.register(sessionId2, subscription, content2);
            }

            for (String subscription: subscriptionIdList){
                repository.register(sessionId3, subscription, content3);
            }


        }

        @Test
        @DisplayName("컨텐츠 리스트를 받아와서 해당하는 모든 컨텐츠의 시청자를 조회")
        void countWithMultipleContentsTest(){
            Map<String, Long> result = repository
                    .getWatcherCountWithMultipleContents(List.of(content1,content2));

            for (Map.Entry<String, Long> entry : result.entrySet()){
                assertThat(entry.getKey()).isIn(content1,content2);
                assertThat(entry.getValue()).isEqualTo(3L);
            }

        }
    }

}
